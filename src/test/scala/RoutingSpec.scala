import akka.actor.{ActorSystem, Props}
import akka.routing.RoundRobinPool
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import routing.RoundRobin

/**
  * Created by Ilya Volynin on 11.10.2019 at 10:11.
  */
class RoutingSpec extends TestKit(ActorSystem("RoutingSpec"))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "RoutingSpec" can {
    val router = system.actorOf(RoundRobinPool(2).props(Props[RoundRobin]))
    "accept requests, handle them in round-robin manner" in {
      for (i <- 1 to 4) {
        router ! s"Hello $i"
      }
    }
  }
}
