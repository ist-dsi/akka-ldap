package pt.tecnico.dsi.ldap.akka

import pt.tecnico.dsi.ldap.akka.Ldap._

class DeduplicationSpec extends ActorSystemSpec {
  val cn = "cn=John Doe"

  "The side-effect" must {
    "only be executed once" when {
      "messages are sent in a ping-pong manner" in {

        val addEntryId = nextSeq()
        ldapActor ! AddEntry(cn, Map("cn" -> List("John Doe"), "sn" -> List("Doe"), "telephoneNumber" -> List("210000000"),
          "objectclass" -> List("person")), deliveryId = addEntryId)
        expectMsg(Successful(addEntryId))

        val replaceAttributeId = nextSeq()
        ldapActor ! ReplaceAttributes(cn, Map("sn" -> List("Doete")), deliveryId = replaceAttributeId)
        expectMsg(Successful(replaceAttributeId))

        val removeAttributeId = nextSeq()
        ldapActor ! RemoveAttributes(cn, List("telephoneNumber"), deliveryId = removeAttributeId)
        expectMsg(Successful(removeAttributeId))

        val deleteEntryId = nextSeq()
        ldapActor ! DeleteEntry(cn, deleteEntryId)
        expectMsg(Successful(deleteEntryId))

      }
      "messages are sent in rapid succession" in {
        val addEntryId = nextSeq()
        ldapActor ! AddEntry(cn, Map("cn" -> List("John Doe"), "sn" -> List("Doe"), "telephoneNumber" -> List("210000000"),
          "objectclass" -> List("person")), deliveryId = addEntryId)
        val replaceAttributeId = nextSeq()
        ldapActor ! ReplaceAttributes(cn, Map("sn" -> List("Doete")), deliveryId = replaceAttributeId)
        val removeAttributeId = nextSeq()
        ldapActor ! RemoveAttributes(cn, List("telephoneNumber"), removeAttributeId)
        val deleteEntryId = nextSeq()
        ldapActor ! DeleteEntry(cn, deleteEntryId)
        ldapActor ! DeleteEntry(cn, deleteEntryId)

        expectMsg(Successful(addEntryId))
        expectMsg(Successful(replaceAttributeId))
        expectMsg(Successful(removeAttributeId))
        expectMsg(Successful(deleteEntryId))
        expectMsg(Successful(deleteEntryId)) //This only works because we are retrying.
      }
    }
    "not be executed" when {
      "a future message is sent" in {
        nextSeq() //Skip one message
        ldapActor ! Search(cn, "uid=john", deliveryId = nextSeq())
        expectNoMsg()
      }
    }
  }
}