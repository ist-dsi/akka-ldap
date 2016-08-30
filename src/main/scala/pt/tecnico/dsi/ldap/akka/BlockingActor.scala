package pt.tecnico.dsi.ldap.akka

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging}
import pt.tecnico.dsi.ldap.{Entry, Ldap ⇒ LdapCore, Settings ⇒ LdapSettings}
import pt.tecnico.dsi.ldap.akka.Ldap._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}

class BlockingActor(val ldapSettings: LdapSettings) extends Actor with ActorLogging {
  val timeout = ldapSettings.connectionTimeout.plus(ldapSettings.responseTimeout)

  //The ldap operations will run in this ExecutionContext
  import context.dispatcher

  val ldap = new LdapCore(ldapSettings)

  def runFuture[R](deliveryId: DeliveryId, future: ⇒ Future[R]): Unit = {
    val f = future.map {
      case () => Successful(deliveryId)
      case entries: Seq[_] ⇒ SearchResponse(entries.asInstanceOf[Seq[Entry]], deliveryId)
    } recover {
      case t: Exception => Failed(t, deliveryId)
    }

    val result = Await.result(f, new FiniteDuration(timeout.toMillis, TimeUnit.MILLISECONDS) * 2)

    context.parent ! SideEffectResult(sender(), result)
  }


  def receive: Receive = {
    case r: Retry => context.parent forward r

    case AddEntry(dn, textAttributes, binaryAttributes, deliveryId) ⇒
      runFuture(deliveryId, ldap.addEntry(dn, textAttributes, binaryAttributes))
    case DeleteEntry(dn, deliveryId) ⇒
      runFuture(deliveryId, ldap.deleteEntry(dn))

    case Search(dn, filter, returningAttributes, size, deliveryId) ⇒
      runFuture(deliveryId, ldap.search(dn, filter, returningAttributes, size))
    case SearchAll(dn, filter, returningAttributes, deliveryId) ⇒
      runFuture(deliveryId, ldap.searchAll(dn, filter, returningAttributes))

    case AddAttributes(dn, textAttributes, binaryAttributes, deliveryId) ⇒
      runFuture(deliveryId, ldap.addAttributes(dn, textAttributes, binaryAttributes))
    case ReplaceAttributes(dn, textAttributes, binaryAttributes, deliveryId) ⇒
      runFuture(deliveryId, ldap.replaceAttributes(dn, textAttributes, binaryAttributes))
    case RemoveAttributes(dn, attributes, deliveryId) ⇒
      runFuture(deliveryId, ldap.removeAttributes(dn, attributes))
  }
}
