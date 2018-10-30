package fsm

import akka.actor.{FSM, LoggingFSM}
import akka.event.LoggingReceive
import scala.concurrent.duration._

object Kote{
  sealed trait State
  sealed trait Data
  object State{
    case object Sleeping extends State
    case object Awake extends State
    case object VeryHungry extends State
  }
  object Data{
    case object Empty extends Data
    case class VitalSigns(hunger: Int) extends Data
  }
  object Commands {
    case object WakeUp
    case object Stroke
    case object FallASleep
    case class GrowHungry(by: Int)
  }
}
class Kote() extends FSM[Kote.State,Kote.Data] with LoggingFSM[Kote.State,Kote.Data]{
import Kote._
  startWith(State.Sleeping, Data.VitalSigns(hunger = 60) )
//  override def receiveCommand: Receive = {
  when(State.Sleeping, 1.second) {
      case Event(Commands.WakeUp |StateTimeout,_) =>
        log.info("sleeping:wakeup")
        goto(State.Awake)
    }
  when(State.Awake){
    case Event(Commands.Stroke,Data.VitalSigns(hunger)) =>
      log.info(s"stroke, hunger $hunger")
      var replMsg = ""
      if (hunger>=30) replMsg="mewww" else replMsg="purrr"
      stay() replying replMsg
  }
  when(State.VeryHungry) {
    case Event(Commands.GrowHungry(by), Data.VitalSigns(hunger)) =>
      val newHunger = hunger+by
    if (newHunger<100)
      stay() using Data.VitalSigns(newHunger) replying newHunger
    else
      throw new RuntimeException("They killed the kitty!")
  }

  whenUnhandled{
    case Event(Commands.GrowHungry(by),Data.VitalSigns(hunger)) =>
      val newHunger = hunger+by
      if (newHunger<85)
        stay() using Data.VitalSigns(newHunger) replying newHunger
      else  if (newHunger<100)
        goto(State.VeryHungry) using Data.VitalSigns(newHunger) replying newHunger
      else
        throw new RuntimeException("They killed the kitty!")
    case Event(Commands.FallASleep, Data.VitalSigns(hunger)) =>
      goto(State.Sleeping) replying hunger

  }

  onTransition {
  case State.Sleeping -> State.Awake =>
  log.info("reaction Meow!")
  case State.VeryHungry -> State.Sleeping | State.Awake-> State.Sleeping  =>
  log.info("reaction Zzzzz...")
  }


}
