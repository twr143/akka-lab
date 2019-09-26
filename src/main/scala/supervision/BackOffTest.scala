package supervision
/*
created by Ilya Volynin at 25.01.18
*/
import java.util.UUID
import akka.{Done, NotUsed}
import akka.actor._
import akka.pattern.Patterns.after
import akka.pattern.{Backoff, BackoffSupervisor}
import scala.concurrent.duration._
import akka.pattern._
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.Timeout
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success
import scala.util.control.NonFatal

/**
  * Demonstration of different supervision strategies
  */
class Kenny(completion: Promise[Done])(implicit logger: Logger) extends Actor {

  val id: String = UUID.randomUUID().toString

  override def preStart(): Unit = logger.warn(s"kenny prerestart id: $id, name : ${context.self.path}")

  def receive: PartialFunction[Any, Unit] = {
    case "exc" => throw new MyException("exc happened!", completion)
    case "complete" =>
      logger.warn(s"complete received! name : ${context.self.path}")
      completion.complete(Success(Done))
    case _ => logger.warn(s"Kenny received a message name : ${context.self.path}")
  }
}

object Kenny {

  def props(completion: Promise[Done])(implicit logger: Logger) = Props(new Kenny(completion))
}

case class MyException(msg: String, completion: Promise[Done]) extends Exception(msg)

class MainSup(implicit logger: Logger) extends Actor {
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: MyException =>
        logger.warn("myexception in mainSup, terminating actor system!")
        context.system.terminate()
        e.completion.complete(Success(Done))
        SupervisorStrategy.Stop
      case _: Exception =>
        SupervisorStrategy.Escalate
      case _ => SupervisorStrategy.Escalate
    }

  def receive: PartialFunction[Any, Unit] = {
    case actorPropsName: (Props, String) ⇒
      context.child(actorPropsName._2) match {
        case Some(actorRef) =>
          logger.warn(s"already exists, retirning! ${actorRef.path}")
          sender() ! actorRef
        case None => sender() ! context.actorOf(actorPropsName._1, actorPropsName._2)
      }
  }
}

object MainSup {

  def props()(implicit logger: Logger) = Props(new MainSup())
}

object BackOffTest extends StreamWrapperApp2 {

  // create the ActorSystem instance
  implicit val timeout: Timeout = Timeout(3.seconds)

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val completion = Promise[Done]()
    val kennyProps = Kenny.props(completion)
    val supervisor = BackoffSupervisor.props(
      Backoff.onFailure(
        kennyProps, "Kenny", 2.seconds,
        maxBackoff = 7.seconds,
        randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
      ) // the child must send BackoffSupervisor.Reset to its parent
        .withSupervisorStrategy(
        OneForOneStrategy() {
          case _: MyException =>
            logger.warn("myexception happened")
            SupervisorStrategy.Restart // this is subjected to play with
          case _: Exception =>
            SupervisorStrategy.Escalate
          case _ => SupervisorStrategy.Stop
        }))
    val mainSup = as.actorOf(MainSup.props(), name = "MainSuper")
    for {
      k <- (mainSup ? (supervisor, "k1")).mapTo[ActorRef].map { ken => ken ! "exc"; ken }
      k <- after(1000.millis, as.scheduler, ec, Future({
        k ! "msg";
        k
      }))
      k <- after(800.millis, as.scheduler, ec, Future({
        k ! "msg";
        k
      }))
      k <- after(800.millis, as.scheduler, ec, Future({
        k ! "msg";
        k
      }))
      k <- after(800.millis, as.scheduler, ec, Future({
        k ! "msg";
        k
      }))
      k <- after(800.millis, as.scheduler, ec, Future({
        k ! "msg";
        k
      }))
      k <- (mainSup ? (supervisor, "k1")).mapTo[ActorRef]
      k <- after(800.millis, as.scheduler, ec, Future({
        k ! "complete";
        k
      }))
    } yield ()
    // unfortunatelly the stream wouldn't start (since the ken actor isn't alive) :(
    //.map(ken => StreamAfterRecovery(ken))
    completion.future
  }
}

