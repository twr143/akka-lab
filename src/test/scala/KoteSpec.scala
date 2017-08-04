import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import kote.Kote
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}
import sun.util.logging.LoggingSupport

class KoteSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with Matchers with FreeSpecLike with BeforeAndAfterAll {
  def this() = this(ActorSystem("KoteSpec"))
  import kote.Kote._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A Kote actor" - {
    // All future tests go here
  }
  "should sleep at birth" in {
    val kote = TestFSMRef(new Kote)
    kote.stateName should be(State.Sleeping)
    kote.stateData should not be(Data.Empty)
  }
  class TestedKote {
    val kote = TestFSMRef(new Kote)
  }
  trait AwakeKoteState extends TestedKote {
    def initialHunger: Int
    kote.setState(State.Awake, Data.VitalSigns(initialHunger))
  }

  trait FullUp {
    def initialHunger: Int = 15
  }
  trait Hungry {
    def initialHunger: Int = 75
  }

  trait ModerateHungry {
    def initialHunger: Int = 55
  }


  "should purr on stroke if not hungry" in new AwakeKoteState with FullUp {
    kote ! Commands.Stroke
    expectMsg("purrr")
    kote.stateName should be(State.Awake)
  }
  "should mewww on stroke if hungry" in new AwakeKoteState with Hungry {
    kote ! Commands.Stroke
    expectMsg("mewww")
    kote.stateName should be(State.Awake)
  }

  "should eventually die from hunger" in new AwakeKoteState with ModerateHungry {
      kote ! Commands.GrowHungry(6)
      expectMsg[Int](61)
      kote ! Commands.GrowHungry(6)
      expectMsg[Int](67)
      kote ! Commands.GrowHungry(6)
      expectMsg[Int](73)
      kote ! Commands.GrowHungry(6)
      expectMsg[Int](79)
      kote ! Commands.GrowHungry(6)
      expectMsg[Int](85)
      kote.stateName should be(State.VeryHungry)
      kote ! Commands.GrowHungry(6)
      expectMsg[Int](91)
      kote ! Commands.GrowHungry(6)
      expectMsg[Int](97)
      intercept[RuntimeException] {
        kote.receive(Commands.GrowHungry(6))
      }


  }

    "go to sleep" in new AwakeKoteState with ModerateHungry {
        kote ! Commands.FallASleep
        kote ! Commands.FallASleep
    }


}