package fanoutin
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import fanoutin.MultiplexActor._
import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
/**
  * Created by Ilya Volynin on 10.06.2018 at 8:35.
  */
class MultiplexActor extends Actor with ActorLogging {
  implicit val ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {
    case c: CommandToMultiplex =>
      log.debug("incoming {}", c.data)
      // no real reason to fan in into the same actor
      // since actor queue will be shared among incoming and outgoing messages
      pipe(parallelCalc(sender(), c)).to(self)
    case c: CommandToDeMultiplex =>
      log.debug("outgoing {}", c.processedData)
      c.sender ! c.processedData
  }

  private def parallelCalc(sender: ActorRef, cmd: CommandToMultiplex): Future[CommandToDeMultiplex] = {
    Future.successful(CommandToDeMultiplex(sender, ProcessedData(cmd.data.counter * (-1))))
  }
}
object MultiplexActor {
  def props(): Props = Props(new MultiplexActor())
  case class CommandToMultiplex(data: MultiplexData)
  case class CommandToDeMultiplex(sender: ActorRef, processedData: ProcessedData)
  case class MultiplexData(counter: Int)
  case class ProcessedData(counter: Int)
}
