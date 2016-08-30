package pt.tecnico.dsi.ldap.akka

import java.io.File

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

abstract class ActorSystemSpec extends TestKit(ActorSystem("akka-ldap", ConfigFactory.load()))
  with Matchers
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with LazyLogging {

  val settings = new Settings()

  val ldapActor = system.actorOf(Props(new LdapActor(settings)), "ldap")

  private var seqCounter = 0L
  def nextSeq(): Long = {
    val ret = seqCounter
    seqCounter += 1
    ret
  }

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.journal.leveldb-shared.store.dir",
    "akka.persistence.snapshot-store.local.dir"
  ).map(s ⇒ new File(system.settings.config.getString(s)))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    storageLocations.foreach(FileUtils.deleteDirectory)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    storageLocations.foreach(FileUtils.deleteDirectory)
    shutdown(verifySystemShutdown = true)
  }
}
