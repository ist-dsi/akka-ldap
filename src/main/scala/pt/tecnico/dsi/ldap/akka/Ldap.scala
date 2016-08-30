package pt.tecnico.dsi.ldap.akka

import pt.tecnico.dsi.ldap.Entry

object Ldap {
  type DeliveryId = Long
  type TextAttributes = Map[String, Seq[String]]
  type BinaryAttributes = Map[String, Seq[Array[Byte]]]
  
  sealed trait Request {
    def deliveryId: DeliveryId
  }
  
  //If removeId is a Some then just the result for (senderPath, removeId) is removed
  //Otherwise all the results for the senderPath are removed.
  case class RemoveDeduplicationResult(removeId: Option[DeliveryId], deliveryId: DeliveryId) extends Request
  
  case class AddEntry(dn: String, textAttributes: TextAttributes = Map.empty, binaryAttributes: BinaryAttributes = Map.empty, deliveryId: DeliveryId) extends Request
  case class DeleteEntry(dn: String, deliveryId: DeliveryId) extends Request
  
  case class Search(dn: String, filter: String, returningAttributes: Seq[String] = Seq.empty, size: Int = 1, deliveryId: DeliveryId) extends Request
  case class SearchAll(dn: String, filter: String, returningAttributes: Seq[String] = Seq.empty, deliveryId: DeliveryId) extends Request
  
  case class AddAttributes(dn: String, textAttributes: TextAttributes = Map.empty, binaryAttributes: BinaryAttributes = Map.empty, deliveryId: DeliveryId) extends Request
  case class ReplaceAttributes(dn: String, textAttributes: TextAttributes = Map.empty, binaryAttributes: BinaryAttributes = Map.empty, deliveryId: DeliveryId) extends Request
  case class RemoveAttributes(dn: String, attributes: Seq[String], deliveryId: DeliveryId) extends Request
  
  sealed trait Response extends Serializable {
    def deliveryId: DeliveryId
  }
  
  trait SuccessResponse extends Response
  case class Successful(deliveryId: DeliveryId) extends SuccessResponse
  case class SearchResponse(entries: Seq[Entry], deliveryId: DeliveryId) extends SuccessResponse
  
  trait FailureResponse extends Response
  case class Failed(exception: Exception, deliveryId: DeliveryId) extends FailureResponse
}
