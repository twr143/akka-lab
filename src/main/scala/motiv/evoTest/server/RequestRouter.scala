package motiv.evoTest.server
/**
  * Created by Ilya Volynin on 28.10.2018 at 13:51.
  */
import java.util.UUID
import akka.actor.{Actor, ActorRef}
import motiv.evoTest.Model.{Incoming, Outgoing}
import akka.pattern._
import akka.util.Timeout
import ch.qos.logback.classic.Logger
import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

object RequestRouter {

  case class Connected(outgoing: ActorRef)

  case class IncomingMessage(obj: Incoming, reqId: UUID, routerActor: ActorRef)

  case class OutgoingMessage(obj: Outgoing)

  implicit val timeout: Timeout = Timeout(3.seconds)
}

class RequestRouter(manager: ActorRef, stateHolder: ActorRef) extends Actor {
  import RequestRouter._

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  def receive = {
    case Connected(outgoing) =>
      manager ! RouterManager.Join
      context.become(connected(outgoing))
  }

  def connected(outgoing: ActorRef): Receive = {
    {
      case im: IncomingMessage =>
        (stateHolder ? im).mapTo[Outgoing].foreach(outgoing ! OutgoingMessage(_))
      case om: OutgoingMessage =>
        outgoing ! om
    }
  }
}