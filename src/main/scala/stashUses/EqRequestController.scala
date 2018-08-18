package stashUses
import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}
import akka.pattern._
import stashUses.EqRequestController._
import scala.collection.mutable._
import scala.concurrent.Future
import akka.util.Timeout
import scala.concurrent.duration._
/**
  * Created by Ilya Volynin on 17.08.2018 at 21:27.
  *
  * The idea of an actor is that equal events should be processed sequentialy,
  * while different ones may be processed in paralel
  *
  * Thus we use set of currently processed objects (processingRequestsSet) to prevent duplicate objects
  * from cuncurrent processing with existing ones
  *
  * In case the duplicate object comes into the process, we stash it, waiting while the processing of its sibling is done
  * Unstash is being caled periodically
  *
  */
class EqRequestController(system: ActorSystem) extends Actor with ActorLogging with Stash {
  val processingRequestsSet: Set[Int] = Set.empty

  implicit val ec = system.dispatcher

  val unstashTask = context.system.scheduler.schedule(1.seconds, 1.seconds, self, UnstashTask)

  override def receive = {
    case Request(value, mark) =>
      log.info("new request {} set {}, thread {}", (value, mark), processingRequestsSet, Thread.currentThread().getId)
      if (!processingRequestsSet.contains(value)) {
        processingRequestsSet += value
        pipe(for {
          result <- processingRoutine(value, mark)
          _ <- Future {
            self ! RemoveFromPreocessingRequestsSet(value)
          }
        } yield result).to(sender())
      } else {
        stash()
        log.info("event {} stashed {}", (value, mark), Thread.currentThread().getId)
        sender() ! Stashed(value)
      }
    case RemoveFromPreocessingRequestsSet(value) =>
      log.info("event {} removed from the set {}", value, Thread.currentThread().getId)
      processingRequestsSet -= value
    case UnstashTask =>
      log.info("unstash task works")
      unstashAll()
  }

  def processingRoutine(value: Int, mark: String): Future[Processed] = Future {
    log.info("event {} started to be processed {}", (value, mark), Thread.currentThread().getId)
    Thread.sleep(100)
    //    after(500.millis, context.system.scheduler)(Future{
    log.info("event {} has been processed {}", (value, mark), Thread.currentThread().getId)
    Processed(value)
  }
}
object EqRequestController {
  def props(system: ActorSystem): Props = Props(new EqRequestController(system))
  case class Request(value: Int, mark: String)
  case class RemoveFromPreocessingRequestsSet(value: Int)
  case object UnstashTask
  case class Processed(value: Int)
  case class Stashed(value: Int)
}
