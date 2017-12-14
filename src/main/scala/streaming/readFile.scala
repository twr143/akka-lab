package streaming

import java.io.File
import java.nio.file.Paths

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing}
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

/*
created by Ilya Volynin at 14.12.17
*/
object readFile extends App {
  implicit val system = ActorSystem("system")
  implicit val materializer = ActorMaterializer()

  def extractId(s: String) = {
    val a = s.split(",")
    a(0) -> a(1)
  }

  private val lineByLineSource = FileIO.fromPath(Paths.get("tmp/example1.csv"))
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024))
    .map(_.utf8String).recover {
      case NonFatal(e) => "file not found"
    }
  val future: Future[Done] = lineByLineSource//.filter(_.nonEmpty).filter(_.contains(","))
    .map(extractId)
    .scan((false, "", ""))((l, r) => (l._2 != r._1, r._1, r._2))
    .drop(1)
    .splitWhen(_._1)
    .fold(("", Seq[String]()))((l, r) => (r._2, l._2 ++ Seq(r._3)))
    .concatSubstreams
    .runForeach(println)
  private val reply = Await.result(future, 10 seconds)
  println(s"Received $reply")
  Await.ready(system.terminate(), 10 seconds)
}
