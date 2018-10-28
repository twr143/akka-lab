package motiv.evoTest
/**
  * Created by Ilya Volynin on 28.10.2018 at 13:51.
  */
import akka.actor.{Actor, ActorRef}
import motiv.evoTest.Model.Outgoing
import motiv.evoTest.RouterManager.Notification

object RequestRouter {

  case class Connected(outgoing: ActorRef)

  case class IncomingMessage(obj: Outgoing)

  case class OutgoingMessage(obj: Outgoing)

}

class RequestRouter(manager: ActorRef) extends Actor {
  import RequestRouter._

  def receive = {
    case Connected(outgoing) =>
      manager ! RouterManager.Join
      context.become(connected(outgoing))
  }

  def connected(outgoing: ActorRef): Receive = {
    {
      case IncomingMessage(obj) =>
        outgoing ! OutgoingMessage(obj)
    }
  }
}