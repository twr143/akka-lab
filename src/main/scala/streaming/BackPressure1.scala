package streaming
import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props, Scheduler}
import akka.dispatch.forkjoin.ThreadLocalRandom
import akka.pattern.Patterns.after
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import streaming.Consumer2._
import akka.pattern.Patterns._
import akka.stream.{ActorMaterializer, KillSwitches}
import streaming.Consumer.Ack
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
object BackPressure1 extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val scheduler = system.scheduler
  val consumer = system.actorOf(Consumer.props(ec, scheduler, system), "total")
  val consumer2 = system.actorOf(Consumer.props(ec, scheduler, system), "total2")
  val source = Source.fromIterator { () => Iterator.range(0, 10000) }.take(40000).watchTermination()((_, futDone) ⇒
    futDone.onComplete {
      case Success(a) ⇒
        println(s"stream successfully completed $a")
      //system.terminate()
      case Failure(t) ⇒
        println(s"failure happened $t ")
        system.terminate()
    })
  val toCons = Flow[Int].map(i => {
    if (i < 490000) Process(i, 0) else Complete(i)
  })
  val sinkConsumer = Sink.actorRefWithAck(consumer, Init, Ack, Complete(1000), errorHandler)
  val sinkConsumer2 = Sink.actorRefWithAck(consumer2, Init, Ack, Complete(1000), errorHandler)
  val lastSnk = Sink.last[Any]
  val ((killSwitch, last), NotUsed) = source.via(toCons).viaMat(KillSwitches.single)(Keep.right).alsoToMat(lastSnk)(Keep.both)
    .toMat(sinkConsumer)(Keep.both).run()
  //          toCons/*.alsoTo(sinkConsumer2)*/.viaMat(KillSwitches.single)(Keep.right).toMat(sinkConsumer)(Keep.right).runWith(source)
  Thread.sleep(80)
  killSwitch.shutdown()
  last.onComplete {
    case Success(Process(l,_)) => println(s"the last one is $l")
    case Success(x) => println(s"completed with unexpected message $x")
    case Failure(t) => println("An error has occured whil completion of last: " + t.getMessage)
  }
}
object Consumer {
  case object Init
  case object Ack
  case class Complete(id: Long)
  case class Process(value: Long, lastMessage: Long)
  def errorHandler(ex: Throwable): Unit = {
    ex match {
      case NonFatal(e) => println("exception happened: " + e)
    }
  }
  def props(implicit ec: ExecutionContext, scheduler: Scheduler, system: ActorSystem): Props = Props(new Consumer()(ec, scheduler, system))
}
class Consumer(implicit ec: ExecutionContext, scheduler: Scheduler, system: ActorSystem) extends Actor {
  override def receive: Receive = {
    case _: Init.type =>
      println(s"init")
      sender ! Ack
    case Process(value, _) =>
      println(s"v=$value :${Thread.currentThread().getId}")
      if (value > 450000) sender ! errorHandler(new IllegalStateException("too large value"))
      sender ! Ack
    case Complete(id) =>
      println(s"completed $id")
      system.terminate()
      sender ! Ack
  }
}