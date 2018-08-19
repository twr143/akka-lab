import akka.actor.ActorSystem
import akka.routing.RoundRobinPool
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import stashUses.EqRequestController
import stashUses.EqRequestController._
import scala.concurrent.duration._
import org.scalatest.concurrent.Eventually._
/**
  * Created by Ilya Volynin on 18.08.2018 at 8:00.
  */
class EqRequestControllerSpec extends TestKit(ActorSystem("EqRequestControllerSpec"))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "EqRequestController" can {
    val eRCActor = system.actorOf(EqRequestController.props(system = system))
    "accept requests, stash or process them after" in {
      eRCActor ! Request(1, "1")
      eRCActor ! Request(1, "2")
      eRCActor ! Request(2, "1")
      eRCActor ! Request(3, "1")
      eRCActor ! Request(3, "2")
      eRCActor ! Request(3, "3")
      eRCActor ! Request(3, "4")
      eRCActor ! Request(4, "1")
      eRCActor ! Request(2, "2")
      eRCActor ! Request(1, "3")
      eRCActor ! Request(1, "4")
      var done = true
      awaitCond(p = {
        done = !done; done
      }, 4.seconds, interval = 4.seconds)
      expectMsgPF() {
        case Processed(1) => //println("processed 1")
        case Processed(2) => //println("processed 2")
        case Processed(3) => //println("processed 3")
        case Processed(4) => //println("processed 4")
        case Stashed(1) => //println("stashed 1")
        case Stashed(2) => //println("stashed 2")
        case Stashed(3) => //println("stashed 2")
      }
    }
  }
}
