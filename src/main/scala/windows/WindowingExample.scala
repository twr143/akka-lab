package windows
import java.time._
import java.util.TimeZone
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random
/**
  * Created by Ilya Volynin on 23.09.2018 at 10:08.
  */
object WindowingExample {
  def main(args: Array[String]): Unit = {
    implicit val as = ActorSystem()
    implicit val mat = ActorMaterializer()
    val random = new Random()
    val f = Source
      .tick(0.seconds, 1.second, "")//.take(20)
      .map { _ =>
        val now = System.currentTimeMillis()
        val delay = random.nextInt(8)
        MyEvent(now - delay * 1000L)
      }
      .statefulMapConcat { () =>
        //        println(s"statefulMapConcat called")
        val generator = new CommandGenerator()
        ev =>
          //          println(s"statefulMapConcat ev: $ev")
          generator.forEvent(ev)
      }
      .groupBy(64, command => command.w)
      .takeWhile(!_.isInstanceOf[CloseWindow], inclusive = true)
      .fold(AggregateEventData((0L, 0L), 0)) {
        case (agg, OpenWindow(window)) =>

//          println(s"open w for ${windowToText(window)}, current ${tsToString(System.currentTimeMillis())}")
          agg.copy(w = window)
        case (agg, CloseWindow(window)) =>

//          println(s"close w for ${windowToText(window)}, current ${tsToString(System.currentTimeMillis())}")
          agg
        case (agg, AddToWindow(ev, window)) =>
          println(s"add to w ${windowToText(window)} ev: $ev, th ${Thread.currentThread().getId}, current ${tsToString(System.currentTimeMillis())}")

          agg.copy(eventCount = agg.eventCount + 1)
      }
      .async
      .mergeSubstreams
      .runForeach { agg =>
        println(agg.toString)
      }
    try Await.result(f, 60.minutes)
    finally as.terminate()
  }
  case class MyEvent(timestamp: Long){
    override def toString: String = s"${tsToString(timestamp)}"
  }
  type Window = (Long, Long)
  object Window {
    val WindowLength = 10.seconds.toMillis

    val WindowStep = 10.second.toMillis

    val WindowsPerEvent = (WindowLength / WindowStep).toInt

    def windowsFor(ts: Long): Set[Window] = {
      val firstWindowStart = ts - ts % WindowStep - WindowLength + WindowStep
      val result = (for (i <- 0 until WindowsPerEvent) yield
        (firstWindowStart + i * WindowStep,
          firstWindowStart + i * WindowStep + WindowLength)
        ).toSet
      result
    }
  }
  def windowToText(w: Window): String = {
    val zoneId = TimeZone.getDefault.toZoneId
    s" [ ${tsToString(w._1)},${tsToString(w._2)} ] "
  }
  sealed trait WindowCommand {
    def w: Window
  }
  case class OpenWindow(w: Window) extends WindowCommand
  case class CloseWindow(w: Window) extends WindowCommand
  case class AddToWindow(ev: MyEvent, w: Window) extends WindowCommand
  class CommandGenerator {
    private val MaxDelay = 5.seconds.toMillis

    private var watermark = 0L

    private val openWindows = mutable.Set[Window]()

    def forEvent(ev: MyEvent): List[WindowCommand] = {
      watermark = math.max(watermark, ev.timestamp - MaxDelay)
//      println(s"watermark for $ev is ${tsToString(watermark)}, cur ${tsToString(System.currentTimeMillis())}")
      if (ev.timestamp < watermark) {
        println(s"Dropping event with timestamp: ${tsToString(ev.timestamp)}")
        Nil
      } else {
        val eventWindows = Window.windowsFor(ev.timestamp)
        val closeCommands = openWindows.flatMap { ow =>
          if (!eventWindows.contains(ow) && ow._2 < watermark) {
            openWindows.remove(ow)
            Some(CloseWindow(ow))
          } else None
        }
        //        println(s"closeCommands for $ev: $closeCommands")
        val openCommands = eventWindows.flatMap { w =>
          if (!openWindows.contains(w)) {
            openWindows.add(w)
            Some(OpenWindow(w))
          } else None
        }
        //        println(s"openCommands for $ev: $openCommands")
        val addCommands = eventWindows.map(w => AddToWindow(ev, w))
        //        println(s"addCommands for $ev: $openCommands")
        openCommands.toList ++ closeCommands.toList ++ addCommands.toList
      }
    }
  }
  case class AggregateEventData(w: Window, eventCount: Int) {
    override def toString =
      s"Between ${tsToString(w._1)} and ${tsToString(w._2)}, there were $eventCount events."
  }
  def tsToString(ts: Long) = OffsetDateTime
    .ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
    .toLocalTime
    .toString
}
