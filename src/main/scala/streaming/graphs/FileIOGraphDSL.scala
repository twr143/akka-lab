package streaming.graphs
import java.io.File

import akka.actor.ActorSystem
import akka.dispatch.forkjoin.ThreadLocalRandom
import akka.stream.scaladsl.{Broadcast, FileIO, Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.util.ByteString

import scala.util.{Failure, Success}

/**
  * Created by Ilya Volynin on 01.10.2018 at 11:40.
  */
/*
created by Ilya Volynin at 18.12.17
*/
object FileIOGraphDSL extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val source = Source.fromIterator { () => Iterator.continually(ThreadLocalRandom.current().nextInt(500000)) }
  val fileSink = FileIO.toFile(new File("random.txt"))

  val slowSink = Flow[Int].map(i => {
    Thread.sleep(1000)
    ByteString(i.toString + "\n")
  }).toMat(fileSink)((_, bytesWritten) => bytesWritten)

  val consoleSink = Sink.foreach[Int](println)

  val graph = GraphDSL.create(slowSink, consoleSink)((slow, _) => slow) { implicit builder =>
    (slow, console) =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      source ~> broadcast ~> slow
      broadcast ~> console
      ClosedShape
  }

  val materialized = RunnableGraph.fromGraph(graph).run()
  materialized.onComplete {
    case Success(_) =>
      system.terminate()
    case Failure(e) =>
      println(s"Failure: ${e.getMessage}")
      system.terminate()
  }
}
