package supervision
/*
created by Ilya Volynin at 25.01.18
*/
import java.util.UUID
import akka.actor._
import akka.pattern.{Backoff, BackoffSupervisor}
import supervision.BackOffTest.{kennyProps, system}
import scala.concurrent.duration._
import akka.pattern._
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Demonstration of different supervision strategies
  */
class Kenny() extends Actor {

  val id = UUID.randomUUID().toString

  override def preStart(): Unit = println(s"kenny prerestart $id")

  def receive = {
    case "exc" => throw new MyException("exc happened!")
    case _ => println("Kenny received a message")
  }
}
object Kenny {

  def props(): Props = Props(new Kenny())
}
class MyException(msg: String) extends Exception(msg)
class MainSup extends Actor {
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: MyException =>
        println("myexception in mainSup, terminating actor system!")
        context.system.terminate()
        SupervisorStrategy.Stop
      case _: Exception =>
        SupervisorStrategy.Escalate
      case _ => SupervisorStrategy.Escalate
    }

  def receive = {
    case p: Props ⇒ sender() ! context.actorOf(p)
  }
}
object BackOffTest extends App {

  // create the ActorSystem instance
  implicit val timeout = Timeout(3.seconds)

  val system = ActorSystem("DeathWatchTest")

  val kennyProps = Kenny.props()

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

  val mainSup = system.actorOf(Props[MainSup], name = "MainSuper")

  (mainSup ? supervisor).mapTo[ActorRef].map {
    superV =>
      superV ! "exc"
      Thread.sleep(1000)
      for (i <- 1 to 8) {
        superV ! "msg"
        Thread.sleep(800)
      }
      system.terminate()
  }
}