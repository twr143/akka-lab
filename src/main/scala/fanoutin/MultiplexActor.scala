package fanoutin
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.Patterns.after
import fanoutin.MultiplexActor._

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._

import scala.concurrent.duration._
/**
  * Created by Ilya Volynin on 10.06.2018 at 8:35.
  */
class MultiplexActor extends Actor with ActorLogging {
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system = ActorSystem()

  override def receive: Receive = {
    case c: CommandToMultiplex =>
      log.info("incoming {}", c.data)
      // no real reason to fan in into the same actor
      // since actor queue will be shared among incoming and outgoing messages
      pipe(parallelCalc(sender(), c)).to(self)
    case c: CommandToDeMultiplex =>
      log.info("outgoing {}", c.processedData)
      c.sender ! c.processedData
  }

  private def parallelCalc(sender: ActorRef, cmd: CommandToMultiplex): Future[CommandToDeMultiplex] = {
    after(900.millis, system.scheduler, ec, Future.successful(CommandToDeMultiplex(sender, ProcessedData(cmd.data.counter * (-1)))))
  }
}
object MultiplexActor {
  def props(): Props = Props(new MultiplexActor())
  case class CommandToMultiplex(data: MultiplexData)
  case class CommandToDeMultiplex(sender: ActorRef, processedData: ProcessedData)
  case class MultiplexData(counter: Int)
  case class ProcessedData(counter: Int)
}
