package supervision
/*
created by Ilya Volynin at 25.01.18
*/
import java.util.UUID
import akka.actor._
import akka.pattern.{Backoff, BackoffSupervisor}
import supervision.BackOffTest.{kennyProps, system}
import scala.concurrent.duration._
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
object BackOffTest extends App {

  // create the ActorSystem instance
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
          SupervisorStrategy.Restart
        case _: Exception =>
          SupervisorStrategy.Escalate
        case _ => SupervisorStrategy.Stop
      }))


  val superV = system.actorOf(supervisor, name = "Super")

  superV ! "exc"

  Thread.sleep(1000)

  for (i <- 1 to 8) {
    superV ! "msg"
    Thread.sleep(800)
  }

    system.terminate()
}