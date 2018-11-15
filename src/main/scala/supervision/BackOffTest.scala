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
import util.StreamWrapperApp
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success
import scala.util.control.NonFatal

/**
  * Demonstration of different supervision strategies
  */
class Kenny(completion: Promise[Done]) extends Actor {

  val id: String = UUID.randomUUID().toString

  override def preStart(): Unit = println(s"kenny prerestart $id")

  def receive: PartialFunction[Any, Unit] = {
    case "exc" => throw new MyException("exc happened!")
    case "complete" =>
      println("complete received!")
      completion.complete(Success(Done))
    case _ => println("Kenny received a message")
  }
}

object Kenny {
  def props(completion: Promise[Done]): Props = Props(new Kenny(completion))
}

class MyException(msg: String) extends Exception(msg)

class MainSup extends Actor {
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  override val supervisorStrategy: OneForOneStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: MyException =>
        println("myexception in mainSup, terminating actor system!")
        context.system.terminate()
        SupervisorStrategy.Stop
      case _: Exception =>
        SupervisorStrategy.Escalate
      case _ => SupervisorStrategy.Escalate
    }

  def receive: PartialFunction[Any, Unit] = {
    case p: Props ⇒ sender() ! context.actorOf(p)
  }
}

object BackOffTest extends StreamWrapperApp {

  // create the ActorSystem instance
  implicit val timeout: Timeout = Timeout(3.seconds)

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    val completion = Promise[Done]()
    val kennyProps = Kenny.props(completion)
    val supervisor = BackoffSupervisor.props(
      Backoff.onFailure(
        kennyProps, "Kenny", 3.seconds,
        maxBackoff = 7.seconds,
        randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
      ) // the child must send BackoffSupervisor.Reset to its parent
        .withSupervisorStrategy(
        OneForOneStrategy() {
          case _: MyException =>
            println("myexception happened")
            SupervisorStrategy.Restart // this is subjected to play with
          case _: Exception =>
            SupervisorStrategy.Escalate
          case _ => SupervisorStrategy.Stop
        }))
    val mainSup = as.actorOf(Props[MainSup], name = "MainSuper")
    (mainSup ? supervisor).mapTo[ActorRef].map { ken => ken ! "exc"; ken }
      .flatMap(ken => after(1000.millis, as.scheduler, ec, Future({ken ! "msg";ken})))
          .flatMap(ken => after(800.millis, as.scheduler, ec, Future({ken ! "msg";ken})))
          .flatMap(ken => after(800.millis, as.scheduler, ec, Future({ken ! "msg";ken})))
          .flatMap(ken => after(800.millis, as.scheduler, ec, Future({ken ! "msg";ken})))
          .flatMap(ken => after(800.millis, as.scheduler, ec, Future({ken ! "msg";ken})))
          .flatMap(ken => after(800.millis, as.scheduler, ec, Future({ken ! "msg";ken})))
          .flatMap(ken => after(800.millis, as.scheduler, ec, Future({ken ! "msg";ken})))
          .flatMap(ken => after(800.millis, as.scheduler, ec, Future({ken ! "complete";ken})))

    // unfortunatelly the stream wouldn't start :(
      //.map(ken => StreamAfterRecovery(ken))
    completion.future
  }

  def sinkConsumer(consumer: ActorRef): Sink[Any, NotUsed] =
    Sink.actorRefWithAck(consumer, "init", "ack", "complete", errorHandler)

  val toCons: Flow[Int, String, NotUsed] = Flow[Int].map(i => "msg")

  // consumer doesn't exist at the moment of stream run (omg)
  // thus the stream doesn't run
  def StreamAfterRecovery(consumer: ActorRef)(implicit actorMaterializer: ActorMaterializer): Unit = {
    Source.fromIterator(() => Iterator.range(1, 9)).via(toCons)
      .throttle(1, 800.millis, 1, ThrottleMode.Shaping)
      .to(sinkConsumer(consumer)).run()
  }

  def errorHandler(ex: Throwable): Unit = {
    ex match {
      case NonFatal(e) => println("exception happened: " + e)
    }
  }
}

