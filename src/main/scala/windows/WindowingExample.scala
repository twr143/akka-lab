package windows
import java.time._
import java.util.TimeZone

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import util.StreamWrapperApp

import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by Ilya Volynin on 23.09.2018 at 10:08.
  *
  * timed windows stats aggregation example
  */
object WindowingExample extends StreamWrapperApp {

  def body()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    val random = new Random()
    val f = Source
      .tick(0.seconds, 1.second, "") .take(20)
      .map { _ =>
      val now = System.currentTimeMillis()
      val delay = random.nextInt(8)
      val id = random.nextInt(3) % 3
      MyEvent(now - delay * 1000L, id)
    }
      .statefulMapConcat { () =>
        //        println(s"statefulMapConcat called")
        val generator = new CommandGenerator()
        ev =>
          //          println(s"statefulMapConcat ev: $ev")
          generator.forEvent(ev)
      }
      .groupBy(64, command => (command.w, command.eventId))
      .takeWhile(!_.isInstanceOf[CloseWindow], inclusive = true)
      .fold(AggregateEventData((0L, 0L, 0L), 0)) {
        case (agg, OpenWindow(window, id)) =>

          println(s"open w for ${windowToText(window)}, $id, current ${tsToString(System.currentTimeMillis())}")
          agg.copy(w = window)
        case (agg, CloseWindow(window, id)) =>

          println(s"close w for ${windowToText(window)}, $id, current ${tsToString(System.currentTimeMillis())}")
          agg
        case (agg, AddToWindow(ev, window, id)) =>
          println(s"add to w ${windowToText(window)}, $id, ev: $ev, th ${Thread.currentThread().getId}, current ${tsToString(System.currentTimeMillis())}")
          agg.copy(eventCount = agg.eventCount + 1)
      }
      .async
      .mergeSubstreams
      .runForeach { agg =>
        println(agg.toString)
      }
    f
  }
  case class MyEvent(timestamp: Long, id: Long) {

    override def toString: String = s"${tsToString(timestamp)}, $id"
  }
  type Window = (Long, Long, Long)
  object Window {

    val WindowLength = 10.seconds.toMillis

    val WindowStep = 10.second.toMillis

    val WindowsPerEvent = (WindowLength / WindowStep).toInt

    def windowsFor(ts: Long, eventId: Long): Set[Window] = {
      val firstWindowStart = ts - ts % WindowStep - WindowLength + WindowStep
      val result = (for (i <- 0 until WindowsPerEvent) yield
        (firstWindowStart + i * WindowStep,
          firstWindowStart + i * WindowStep + WindowLength, eventId)
        ).toSet
      result
    }
  }
  def windowToText(w: Window): String = {
    val zoneId = TimeZone.getDefault.toZoneId
    s" [ ${tsToString(w._1)},${tsToString(w._2)} ] "
  }
  sealed trait WindowCommand {

    def eventId: Long

    def w: Window
  }
  case class OpenWindow(w: Window, eventId: Long) extends WindowCommand
  case class CloseWindow(w: Window, eventId: Long) extends WindowCommand
  case class AddToWindow(ev: MyEvent, w: Window, eventId: Long) extends WindowCommand
  class CommandGenerator {

    private val MaxDelay = 5.seconds.toMillis

    private var watermark = 0L

    private val openWindows = mutable.Set[Window]()

    def forEvent(ev: MyEvent): List[WindowCommand] = {
      watermark = math.max(watermark, ev.timestamp - MaxDelay)
      //      println(s"watermark for $ev is ${tsToString(watermark)}, cur ${tsToString(System.currentTimeMillis())}")
      if (ev.timestamp < watermark) {
        println(s"Dropping event with timestamp: ${tsToString(ev.timestamp)}, wm: $watermark")
        Nil
      } else {
        val eventWindows = Window.windowsFor(ev.timestamp, ev.id)
        val closeCommands = openWindows.flatMap { ow =>
          if (!eventWindows.contains(ow) && ow._2 < watermark) {
            openWindows.remove(ow)
            Some(CloseWindow(ow, eventId = ow._3))
          } else None
        }
        //        println(s"closeCommands for $ev: $closeCommands")
        val openCommands = eventWindows.flatMap { w =>
          if (!openWindows.contains(w)) {
            openWindows.add(w)
            Some(OpenWindow(w, eventId = ev.id))
          } else None
        }
        //        println(s"openCommands for $ev: $openCommands")
        val addCommands = eventWindows.map(w => AddToWindow(ev, w, eventId = ev.id))
        //        println(s"addCommands for $ev: $openCommands")
        openCommands.toList ++ closeCommands.toList ++ addCommands.toList
      }
    }
  }
  case class AggregateEventData(w: Window, eventCount: Int) {

    override def toString =
      s"Between ${tsToString(w._1)} and ${tsToString(w._2)}, ${w._3}, there were $eventCount events."
  }
  def tsToString(ts: Long) = OffsetDateTime
    .ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
    .toLocalTime
    .toString
}
