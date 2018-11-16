package streaming.graphs
import java.io.File
import akka.actor.{Actor, ActorSystem, Props}
import akka.dispatch.forkjoin.ThreadLocalRandom
import akka.stream.scaladsl.{Broadcast, FileIO, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, ThrottleMode}
import akka.util.ByteString
import streaming.graphs.Consumer2._
import util.StreamWrapperApp
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import scala.concurrent.duration._

/*
created by Ilya Volynin at 18.12.17
*/
object FileIOGraphFilterActor extends StreamWrapperApp {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    val consumer = as.actorOf(Props[Consumer2], "total2")
    val sinkConsumer = Sink.actorRefWithAck(consumer, Init, Ack, Complete(100), errorHandler)
    val source = Source
      .fromIterator { () => Iterator.continually(ThreadLocalRandom.current().nextInt(500000)) }
      .take(20)
    val fileSink = FileIO.toPath(new File("random.txt").toPath)
    val slowSink = Flow[Int]
      .throttle(1, 500.millis, 1, ThrottleMode.Shaping)
      .map(i => ByteString(i.toString + "\n"))
      .toMat(fileSink)((_, bytesWritten) => bytesWritten)
    val odd = Flow[Int].filter(_ % 2 == 1)
    val even = Flow[Int].filter(_ % 2 == 0)
    val toCons = Flow[Int].map(Process(_, 0))
    val consoleSink = Sink.foreach[Int](i => println(s"$i: ${Thread.currentThread().getId}"))
    val graph = GraphDSL.create(slowSink, consoleSink)((slow, _) => slow) { implicit builder =>
      (slow, console) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[Int](3))
        source ~> broadcast ~> even ~> slow
        broadcast ~> odd ~> console
        broadcast ~> even ~> toCons ~> sinkConsumer
        ClosedShape
    }
    RunnableGraph.fromGraph(graph).run()
  }
}

object Consumer2 {

  case object Init

  case object Ack

  case class Complete(id: Long)

  case class Process(value: Long, lastMessage: Long)

  def errorHandler(ex: Throwable): Unit = {
    ex match {
      case NonFatal(e) => println("exception happened: " + e)
    }
  }
}

class Consumer2 extends Actor {

  override def receive: Receive = {
    case _: Init.type =>
      println(s"init")
      sender ! Ack
    case Process(value, _) =>
      println(s"v=$value")
      if (value > 450000) sender ! errorHandler(new IllegalStateException("too large value"))
      sender ! Ack
    case Complete(id) =>
      println(s"completed $id")
      sender ! Ack
  }
}
