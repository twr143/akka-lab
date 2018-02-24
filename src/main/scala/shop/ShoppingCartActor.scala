package shop

import akka.actor.Actor.Receive
import akka.actor.{ActorLogging, Props, Status}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import shop.ShoppingCartActor._

class ShoppingCartActor(id: String) extends PersistentActor with ActorLogging {
  private var state: Seq[ShoppingItem] = Seq.empty
  override def persistenceId: String = id


  def initialized: Receive = {
    case UpdateItemCommand(item) =>
      persist(ItemUpdated(item)) { evt =>
        state = applyEvent(evt)
        sender() ! UpdateItemResponse(item)
      }
    case RemoveItemCommand(itemId) =>
      persist(ItemRemoved(itemId)) { evt =>
        state = applyEvent(evt)
        sender() ! RemoveItemResponse(itemId)
      }
    case GetItemsRequest =>
      sender() ! GetItemsResponse(state)
  }

  def uninitialized: Receive = {
    case AddItemCommand(item) =>
      persist(ItemAdded(item)) { evt =>
        state = applyEvent(evt)
        context.become(initialized)
        unstashAll()
        sender() ! AddItemResponse(item)
      }
    case any =>
      if (!recoveryFinished) stash()
      else sender() ! Status.Failure(ItemNotFound)

  }
  override def receiveCommand = uninitialized

  override def receiveRecover: Receive = {
    case ItemAdded(item) =>
      state = item +: state
      context.become(initialized)
    case evt: ShoppingCartEvent => state = applyEvent(evt)
    case RecoveryCompleted =>
      log.info("Recovery completed!")
  }

  private def applyEvent(shoppingCartEvent: ShoppingCartEvent): Seq[ShoppingItem] = shoppingCartEvent match {
    case ItemAdded(item) => item +: state
    case ItemUpdated(item) => item +: state.filterNot(_.id == item.id)
    case ItemRemoved(itemId) => state.filterNot(_.id == itemId)
  }
}
object ShoppingCartActor {
  def props(id: String): Props = Props(new ShoppingCartActor(id))

  //protocol
  case class AddItemCommand(shoppingItem: ShoppingItem)
  case class AddItemResponse(shoppingItem: ShoppingItem)

  case class UpdateItemCommand(shoppingItem: ShoppingItem)
  case class UpdateItemResponse(shoppingItem: ShoppingItem)

  case class RemoveItemCommand(shoppingItemId: String)
  case class RemoveItemResponse(shoppingItemId: String)

  case object GetItemsRequest
  case class GetItemsResponse(items: Seq[ShoppingItem])

  // events
  sealed trait ShoppingCartEvent
  case class ItemAdded(shoppingItem: ShoppingItem) extends ShoppingCartEvent
  case class ItemUpdated(shoppingItem: ShoppingItem) extends ShoppingCartEvent
  case class ItemRemoved(shoppingItemId: String) extends ShoppingCartEvent

  case class ShoppingItem(id:String,desc:String, price:Double, quantity:Int)

  case object ItemNotFound extends Exception
}
