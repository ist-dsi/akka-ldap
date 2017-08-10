package pt.tecnico.dsi.ldap.akka

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.event.LoggingReceive
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.typesafe.config.Config
import pt.tecnico.dsi.ldap.akka.Ldap._
import scala.collection.immutable.SortedMap
import scala.concurrent.duration.Duration

case class Retry(deliveryId: DeliveryId)
case class SideEffectResult(recipient: ActorRef, response: Response)
case class RemoveResult(recipient: ActorRef, removeId: Option[DeliveryId])
case object SaveSnapshot


class LdapActor(val settings: Settings = new Settings()) extends Actor with PersistentActor with ActorLogging {
  def this(config: Config) = this(new Settings(config))

  import settings._

  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: DeliveryId): Unit = {
    println(cause.getLocalizedMessage)
    cause.printStackTrace()
    //super.onPersistRejected(cause, event, seqNr)
  }

  def persistenceId: String = "ldapActor"

  private val blockingActor = context.actorOf(Props(classOf[BlockingActor], ldapSettings))
  //By using a SortedMap as opposed to a Map we can also extract the latest deliveryId per sender
  private var resultsPerSender = Map.empty[ActorPath, SortedMap[DeliveryId, Option[Response]]]

  def resultsOf(senderPath: ActorPath): SortedMap[DeliveryId, Option[Response]] = {
    resultsPerSender.getOrElse(senderPath, SortedMap.empty[Long, Option[Response]])
  }

  def performDeduplication(deliveryId: DeliveryId)(onResend: ⇒ Unit)(onExpected: ⇒ Unit): Unit = {
    val recipient = sender()
    val senderPath = recipient.path
    val expectedId = resultsOf(senderPath).keySet.lastOption.map(_ + 1).getOrElse(0L)

    def logIt(op: String, description: String): Unit = {
      log.debug(s"""Sender: $senderPath
                    |DeliveryId ($deliveryId) $op ExpectedId ($expectedId)
                    |$description""".stripMargin)
    }

    if (deliveryId > expectedId) {
      logIt(">", "Ignoring message.")
    } else if (deliveryId < expectedId) {
      logIt("<", "Got a resend.")
      onResend
    } else { //deliveryId == expectedId
      logIt("=", "Going to perform deduplication.")
      onExpected
    }
  }

  def performDeduplication(request: Request): Unit = {
    val deliveryId = request.deliveryId
    val recipient = sender()
    val senderPath = recipient.path

    performDeduplication(deliveryId) {
      resultsOf(senderPath).get(deliveryId).flatten match {
        case Some(result) =>
          log.debug(s"Resending previously computed result: $result.")
          recipient ! result
        case None =>
          log.debug("There is no previously computed result. Probably it is still being computed. Going to retry.")
          //We schedule the retry by sending it to the blockingActor, which in turn will send it back to us.
          //This strategy as a few advantages:
          // · The retry will only be processed in the blockingActor after the previous expects are executed.
          //   This is helpful since the result in which we are interested will likely be obtained by executing
          //   one of the previous expects.
          // · This actor will only receive the Retry after the SideEffectResults of the previous expects.
          //   Or in other words, the Retry will only be processed after the results are persisted and updated in the
          //   resultsPerSender, guaranteeing we have the result (if it was not explicitly removed).
          blockingActor forward Retry(deliveryId)
      }
    } {
      //By setting the result to None we ensure a bigger throughput since we allow this actor to continue
      //processing requests. Aka the expected id will be increased and we will be able to respond to messages
      //where DeliveryId < expectedID.
      updateResult(senderPath, deliveryId, None)

      //The blockingActor will execute the expect then send us back a SideEffectResult
      blockingActor forward request

      // If we crash:
      //  · After executing the expect
      //  · But before persisting the SideEffectResult
      // then the side-effect will be performed twice.
    }
  }

  def removeResult(senderPath: ActorPath, removeId: Option[DeliveryId]): Unit = {
    resultsPerSender.get(senderPath) match {
      case None => //We dont have any entry for senderPath. All good, we don't need to do anything.
      case Some(previousResults) =>
        removeId match {
          case None => resultsPerSender -= senderPath
          case Some(id) => resultsPerSender += senderPath -> (previousResults - id)
        }
    }
  }
  def updateResult(senderPath: ActorPath, deliveryId: DeliveryId, response: Option[Response]): Unit = {
    val previousResults = resultsPerSender.getOrElse(senderPath, SortedMap.empty[Long, Option[Response]])
    resultsPerSender += senderPath -> previousResults.updated(deliveryId, response)

    // This is not exactly every X SideEffectResult since we also persist RemoveResult.
    // However the number of RemoveResults will be very small compared to the number of SideEffectResults.
    if (saveSnapshotRoughlyEveryXMessages > 0 && lastSequenceNr % saveSnapshotRoughlyEveryXMessages == 0) {
      self ! SaveSnapshot
    }
  }

  def receiveCommand: Receive = LoggingReceive {
    case RemoveDeduplicationResult(removeId, deliveryId) ⇒
      performDeduplication(deliveryId) {
        sender() ! Successful(deliveryId)
      } {
        persist(RemoveResult(sender(), removeId)) { remove ⇒
          if (removeDelay == Duration.Zero) {
            removeResult(remove.recipient.path, remove.removeId)
          } else {
            context.system.scheduler.scheduleOnce(removeDelay) {
              self ! remove
            }(context.dispatcher)
          }
          //TODO: should we send the response and update the result after the delay. That it, instead of right away.
          sender() ! Successful(deliveryId)
          //We can store a None because we know a remove will always be successful
          updateResult(remove.recipient.path, deliveryId, None)
        }
      }

    case RemoveResult(recipient, removeId) ⇒
      removeResult(recipient.path, removeId)

    case result @ SideEffectResult(recipient, response) ⇒
      persist(result) { _ ⇒
        updateResult(recipient.path, response.deliveryId, Some(response))
        recipient ! response
      }

    case Retry(deliveryId) ⇒
      val senderPath = sender().path
      resultsPerSender.get(senderPath).flatMap(_.get(deliveryId).flatten) match {
        case Some(result) ⇒
          log.debug(s"Retry for ($senderPath, $deliveryId): sending result: $result.")
          sender ! result
        case None ⇒
          log.debug(s"Retry for ($senderPath, $deliveryId): still no result. Most probably it was removed explicitly.")
      }

    case SaveSnapshot => saveSnapshot(resultsPerSender)

    case request: Request ⇒ performDeduplication(request)

    case a => sender() ! a
  }

  def receiveRecover: Receive = LoggingReceive {

    case SnapshotOffer(_, offeredSnapshot) =>
      resultsPerSender = offeredSnapshot.asInstanceOf[Map[ActorPath, SortedMap[Long, Option[Response]]]]
    case SideEffectResult(recipient, response) =>
      updateResult(recipient.path, response.deliveryId, Some(response))
    case RemoveResult(recipient, removeId) =>
      removeResult(recipient.path, removeId)
  }
}
