package streaming.graphs
import java.io.File
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}

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

/**
  * Created by Ilya Volynin on 01.10.2018 at 11:40.
  */

object FileIOGraphDSL extends StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val source = Source
      .fromIterator { () => Iterator.continually(ThreadLocalRandom.current().nextInt(500000)) }
      .take(20)
    val fileSink = FileIO.toPath(new File("tmp/random.txt").toPath, options = Set(WRITE, TRUNCATE_EXISTING, CREATE))
    val slowSink =
      Flow[Int]
        .throttle(1, 500.millis, 1, ThrottleMode.Shaping)
        .map(i => ByteString(i.toString + "\n"))
        .toMat(fileSink)((_, bytesWritten) => bytesWritten)
    val consoleSink = Sink.foreach[Int](s => logger.warn(s.toString))
    val graph = GraphDSL.create(slowSink, consoleSink)((slow, _) => slow) { implicit builder =>
      (slow, console) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[Int](2))
        source ~> broadcast ~> slow
        broadcast ~> console
        ClosedShape
    }
    RunnableGraph.fromGraph(graph).run()
  }
}
