package streaming

import java.io.File
import java.nio.file.{Files, Paths}
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
object readFile {
  implicit val system = ActorSystem("system")
  implicit val materializer = ActorMaterializer()

  def extractId(s: String): (String, String) = {
    val a = s.split(",")
    (a(0), a(1))
  }

  def main(args: Array[String]) {
    val p = Paths.get("tmp/example.csv")
    if (Files.notExists(p)) {
      println("file not found")
      system.terminate()
      return
    }
    val lineByLineSource = FileIO.fromPath(p)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024))
      .map(_.utf8String)
    val future: Future[Done] = lineByLineSource.filter(_.nonEmpty).filter(_.contains(","))
      .map(extractId)
      .scan((false, "", ""))((p, n) => {
//        println((p, n))
        (p._2 != n._1, n._1, n._2)
      })
    .drop(1)
      .splitWhen(_._1)
      .fold(("", Seq[String]()))((l, r) =>
      {
        println((l, r))
        (r._2, l._2 ++ Seq(r._3))
      }
      )
      .concatSubstreams.runForeach((a:(String, Seq[String]))=>{})
    val reply = Await.result(future, 10 seconds)
    println(s"Received $reply")
    Await.ready(system.terminate(), 10 seconds)
  }
}
