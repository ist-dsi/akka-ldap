package pt.tecnico.dsi.ldap.akka

import akka.actor.{Actor, ActorLogging}
import akka.persistence.PersistentActor

class LdapActor(val settings: Settings = new Settings()) extends Actor with PersistentActor with ActorLogging {
  
  override def receiveRecover: Receive = ???

  override def receiveCommand: Receive = ???

  override def persistenceId: String = ???
}
