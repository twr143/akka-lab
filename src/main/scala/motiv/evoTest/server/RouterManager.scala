package motiv.evoTest.server

/**
  * Created by Ilya Volynin on 28.10.2018 at 13:56.
  */
import akka.actor._
import motiv.evoTest.Model.Outgoing
import motiv.evoTest.server.RequestRouter.IncomingMessage

object RouterManager {

  case object Join

  case class Notification(subscribers: Set[ActorRef], message: Outgoing)

}

class RouterManager extends Actor {
  import RouterManager._


  def receive = {
    case Join =>
      // we also would like to remove the user when its actor is stopped
      context.watch(sender())
    case Terminated(user) =>
    case msg: Notification =>
      msg.subscribers.foreach(_ ! IncomingMessage(msg.message))
  }
}