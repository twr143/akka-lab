package streaming.files
import java.io.FileNotFoundException
import java.nio.file.{Files, Paths}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing}
import akka.util.ByteString
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/*
created by Ilya Volynin at 14.12.17
*/
object readFileGrouping extends StreamWrapperApp2 {

  def extractId(s: String): (String, String) = {
    val a = s.split(",")
    (a(0), a(1))
  }

  def adjust[A, B](m: Map[A, B], k: A, DefaultValue: B)(f: B => B): Map[A, B] = m.updated(k, f(m.getOrElse(k, DefaultValue)))

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val fileName = "tmp/ungrouped.csv"
    val p = Paths.get(fileName)
    if (Files.notExists(p)) {
      logger.error("file {} not found", fileName)
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
      logger.warn(result.toString)
    }
  }
}
