package streaming.graphs
import java.io.File

import akka.actor.ActorSystem
import akka.dispatch.forkjoin.ThreadLocalRandom
import akka.stream.scaladsl.{Broadcast, FileIO, Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, ThrottleMode}
import akka.util.ByteString
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/*
created by Ilya Volynin at 18.12.17
*/
object FileIOGraphDSLWithFilter extends StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val source = Source
      .fromIterator { () => Iterator.continually(ThreadLocalRandom.current().nextInt(500000)) }
      .take(20)
    val fileSink = FileIO.toPath(new File("tmp/random.txt").toPath)
    val slowSink = Flow[Int]
      .throttle(1, 500.millis, 1, ThrottleMode.Shaping)
      .map(i => ByteString(i.toString + "\n"))
      .toMat(fileSink)((_, bytesWritten) => bytesWritten)
    val odd = Flow[Int].filter(_ % 2 == 1)
    val even = Flow[Int].filter(_ % 2 == 0)
    val consoleSink = Sink.foreach[Int](i => logger.warn(s"$i: ${Thread.currentThread().getId}"))
    val graph = GraphDSL.create(slowSink, consoleSink)((slow, _) => slow) { implicit builder =>
      (slow, console) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[Int](2))
        source ~> broadcast ~> even ~> slow
        broadcast ~> odd ~> console
        ClosedShape
    }
    RunnableGraph.fromGraph(graph).run()
  }
}
