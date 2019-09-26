package streaming
import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorSystem, Props, Scheduler}
import akka.dispatch.forkjoin.ThreadLocalRandom
import akka.pattern.Patterns.after
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.pattern.Patterns._
import akka.stream.{ActorMaterializer, KillSwitches}
import ch.qos.logback.classic.Logger
import streaming.Consumer._
import util.StreamWrapperApp2
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

object BackPressure extends StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val completed = Promise[Done]()
    implicit val scheduler: Scheduler = as.scheduler
    val consumer = as.actorOf(Consumer.props, "total")
    val consumer2 = as.actorOf(Consumer.props, "total2")
    val source = Source.fromIterator { () => Iterator.range(0, 10000) }.take(40000).watchTermination()((_, futDone) ⇒
      futDone.onComplete {
        case Success(a) ⇒
          logger.warn(s"stream successfully completed $a")
        case Failure(t) ⇒
          logger.warn(s"failure happened $t ")
          as.terminate()
      })
    val toCons = Flow[Int].map(i => {
      logger.warn(s"sender v=${i.toString.padTo(3,' ')}:${Thread.currentThread().getId}")
      if (i < 490000) Process(i, 0) else Complete(completed)
    })
    val sinkConsumer = Sink.actorRefWithAck(consumer, Init, Ack, Complete(completed), errorHandler)
    val sinkConsumer2 = Sink.actorRefWithAck(consumer2, Init, Ack, Complete(completed), errorHandler)
    val lastSnk = Sink.last[Any]
    val lastSnk2 = Sink.last[Any]
    // when sink is an actor, then there is no Future[Done] materialization returned
    // instead there is sink.last callback for getting to know the last element completed
    // also 'Completed' event notifies the actor about termination
    val (((killSwitch,last), last2), NotUsed) = source.via(toCons).viaMat(KillSwitches.single)(Keep.right)
      .alsoToMat(lastSnk)(Keep.both).alsoToMat(lastSnk2)(Keep.both)
      .toMat(sinkConsumer)(Keep.both).run()
    //          toCons/*.alsoTo(sinkConsumer2)*/.viaMat(KillSwitches.single)(Keep.right).toMat(sinkConsumer)(Keep.right).runWith(source)
    Thread.sleep(90)
    killSwitch.shutdown()
    last.onComplete {
      case Success(Process(l, _)) => logger.warn(s"the last one is $l")
      case Success(x) => logger.warn(s"completed with unexpected message $x")
      case Failure(t) => logger.warn("An error has occured whil completion of last: " + t.getMessage)
    }
    completed.future
  }
}

object Consumer {

  case object Init

  case object Ack

  case class Complete(completed: Promise[Done])

  case class Process(value: Long, lastMessage: Long)

  def errorHandler(ex: Throwable): Unit = {
    ex match {
      case NonFatal(e) => println("exception happened: " + e)
    }
  }

  def props(implicit ec: ExecutionContext, scheduler: Scheduler, as: ActorSystem, logger: Logger): Props = Props(new Consumer())
}

class Consumer(implicit ec: ExecutionContext, scheduler: Scheduler, as: ActorSystem, logger: Logger) extends Actor {

  override def receive: Receive = {
    case _: Init.type =>
      logger.warn(s"init")
      sender ! Ack
    case Process(value, _) =>
      logger.warn("".padTo(7,' ')+s"v=${value.toString.padTo(3,' ')}:${Thread.currentThread().getId}")
      if (value > 450000) sender ! errorHandler(new IllegalStateException("too large value"))
      sender ! Ack
    case Complete(completed) =>
      completed.complete(Success(Done))
      logger.warn(s"completed.")
      sender ! Ack
  }
}