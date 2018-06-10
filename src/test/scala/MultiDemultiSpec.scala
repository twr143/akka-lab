import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import fanoutin.MultiplexActor
import fanoutin.MultiplexActor._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
/**
  * Created by Ilya Volynin on 10.06.2018 at 9:05.
  */
class MultiDemultiSpec extends TestKit(ActorSystem("MultiDemultiSpec"))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "MultiplexActor" can {
    val mActor = system.actorOf(MultiplexActor.props())
    val mActor2 = system.actorOf(MultiplexActor.props())
    "accept command, multiplex it, process, then fan in into sync block and finelly return a result " in {
      mActor ! CommandToMultiplex(MultiplexData(2))
      mActor ! CommandToMultiplex(MultiplexData(3))
      mActor ! CommandToMultiplex(MultiplexData(4))
      mActor2 ! CommandToMultiplex(MultiplexData(5))
      mActor2 ! CommandToMultiplex(MultiplexData(6))
      expectMsgAllOf(1.second, ProcessedData(-2), ProcessedData(-3), ProcessedData(-4), ProcessedData(-5), ProcessedData(-6))
    }
  }
}
