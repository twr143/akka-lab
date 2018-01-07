package streaming
import akka.actor.{Actor, ActorSystem, Props, Scheduler}
import akka.dispatch.forkjoin.ThreadLocalRandom
import akka.pattern.Patterns.after
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import streaming.Consumer2._
import akka.pattern.Patterns._
import akka.stream.ActorMaterializer
import streaming.Consumer.Ack

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal
object BackPressure1 extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val scheduler = system.scheduler
  val consumer = system.actorOf(Consumer.props(ec,scheduler), "total")
  val consumer2 = system.actorOf(Consumer.props(ec,scheduler), "total2")

  val source = Source.fromIterator { () => Iterator.continually(ThreadLocalRandom.current().nextInt(500000)) }.take(4000).watchTermination()((_, futDone) ⇒
                futDone.onComplete {
                  case Success(_) ⇒
                    println("stream successfully completed")
                    system.terminate()
                  case Failure(t) ⇒
                    println(s"failure happened $t ")
                    system.terminate()
                })
  val toCons = Flow[Int].map(i => {
    if (i<490000) Process(i, 0) else Complete(i)
  })
  val sinkConsumer = Sink.actorRefWithAck(consumer, Init, Ack, Complete(1000), errorHandler)
  val sinkConsumer2 = Sink.actorRefWithAck(consumer2, Init, Ack, Complete(1000), errorHandler)

        //val stream = source.take(100).via(toCons).toMat(sinkConsumer)(Keep.both).run()
          toCons/*.alsoTo(sinkConsumer2)*/.to(sinkConsumer).runWith(source)
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
  def props(implicit ec: ExecutionContext, scheduler: Scheduler): Props = Props(new Consumer()(ec,scheduler))
}

class Consumer(implicit ec: ExecutionContext, scheduler: Scheduler) extends Actor {
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
      sender ! Ack
  }
}


/*class Consumer(implicit ec: ExecutionContext, scheduler: Scheduler) extends Actor {
  override def receive: Receive = {
    case _: Init.type =>
      pipe(init(), ec).to(sender())
    case Process(value, m) =>
      pipe(process(value, m), ec).to(sender())
    case Complete(id) =>
      pipe(complete(id), ec).to(sender())
  }

  def init(): Future[Consumer.Ack] = {
    println(s"init получено")
    Future(Ack())(ec)
//    after(30.millis, scheduler, ec, Future(Ack())(ec))
  }

  def process(value: Long, lastMessage: Long): Future[Any] = {
    println(s"v=$value")
    after(30.millis, scheduler, ec, Future(if (value > 450000)
      errorHandler(new IllegalStateException("too large value")) else Ack())(ec))
  }
  def complete(value: Long): Future[Ack] = {
    println(s"completed $value")
    after(30.millis, scheduler, ec, Future(Ack())(ec))
  }

} */