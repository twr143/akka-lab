package streaming.files
import java.nio.file.{Files, Paths}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing}
import akka.util.ByteString

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/*
created by Ilya Volynin at 14.12.17
*/
object readFileGrouping {
  implicit val system = ActorSystem("system")
  implicit val materializer = ActorMaterializer()

  def extractId(s: String): (String, String) = {
    val a = s.split(",")
    (a(0), a(1))
  }

  def main(args: Array[String]) {
    val p = Paths.get("tmp/ungrouped.csv")
    if (Files.notExists(p)) {
      println("file not found")
      system.terminate()
      return
    }
    val lineByLineSource = FileIO.fromPath(p)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024))
      .map(_.utf8String)
    val future: Future[Done] = lineByLineSource.filter(_.nonEmpty).filter(_.contains(","))
      .map(extractId).fold(Map.empty[String, Int])((l: Map[String, Int], r: (String, String)) => {
      //              println(l)
      //              println(r)
      l + (r._1 -> (1 + l.getOrElse(r._1, 0)))
    }
    ).runForeach {l: Map[String, Int] =>
      val result = l.toList.sortWith(_._2 > _._2)
      println(result)
    }
    val reply = Await.result(future, 10 seconds)
    println(s"Received $reply")
    Await.ready(system.terminate(), 10 seconds)
  }
}
