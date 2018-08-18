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
      //      eventually {
      val probe = TestProbe()
      eRCActor ! Request(1, "1")
      eRCActor ! Request(1, "2")
      eRCActor ! Request(2, "1")
      eRCActor ! Request(1, "3")
      eRCActor ! Request(1, "4")
      Thread.sleep(4000)
      //      expectMsgAllOf(1.second, Processed(1))
      //      expectMsgAllOf(1.second, Processed(1), Processed(2), Stashed(1))
      expectMsgPF() {
        case Processed(1) => //println("processed 1")
        case Processed(2) => //println("processed 2")
        case Stashed(1) => //println("stashed 1")
      }
      //      }
    }
  }
}
