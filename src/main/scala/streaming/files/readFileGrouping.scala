package streaming.files
import java.io.FileNotFoundException
import java.nio.file.{Files, Paths}
import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing}
import akka.util.ByteString
import util.StreamWrapperApp
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/*
created by Ilya Volynin at 14.12.17
*/
object readFileGrouping extends StreamWrapperApp {

  def extractId(s: String): (String, String) = {
    val a = s.split(",")
    (a(0), a(1))
  }

  def adjust[A, B](m: Map[A, B], k: A, DefaultValue: B)(f: B => B): Map[A, B] = m.updated(k, f(m.getOrElse(k, DefaultValue)))

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    val p = Paths.get("tmp/ungrouped.csv")
    if (Files.notExists(p)) {
      println("file not found")
      as.terminate()
      return Future.failed(new FileNotFoundException(p.toAbsolutePath.toString))
    }
    val lineByLineSource = FileIO.fromPath(p)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024))
      .map(_.utf8String)
    lineByLineSource.filter(_.nonEmpty).filter(_.contains(","))
      .map(extractId).fold(Map.empty[String, Int])((l: Map[String, Int], r: (String, String))
    => adjust(l, r._1, 0)(_ + 1)
    ).runForeach { l: Map[String, Int] =>
      val result = l.toList.sortWith(_._2 > _._2)
      println(result)
    }
  }
}
