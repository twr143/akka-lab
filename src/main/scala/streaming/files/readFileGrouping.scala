package streaming.files
import java.io.FileNotFoundException
import java.nio.file.{Files, Path, Paths}

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.javadsl.RunnableGraph
import akka.stream.{ActorMaterializer, IOResult, scaladsl}
import akka.stream.scaladsl.{FileIO, Flow, FlowOps, Framing, Keep, Sink, Source, SubFlow}
import akka.util.ByteString
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2

import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Random, Success}
import better.files.{File => ScalaFile, _}
import java.io.File
import java.nio.charset.Charset

/*
created by Ilya Volynin at 14.12.17
launch: "runMain streaming.files.readFileGrouping "tmp/zarubezhom.txt" 0"
*/
object readFileGrouping extends StreamWrapperApp2 {

  val amendList = List[String => String](_.replaceAll("\"|,|\\.|!|-|\\?", ""),
    //    _.replaceAll("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*)", ""),
    _.replaceAll("ожид", "ожИд"),
    _.replaceAll("(НЕ)?(ж|Ж)(и|ы)д[а-я]{1,15}", "жыды"),
    _.replaceAll("(Р|р)осси[а-я]{1,19}", "Россия"),
    _.replaceAll("(е|Е)вре[а-я]{1,19}", "евреи"),
    _.replaceAll("(г|Г)(о|О)(И|и|я|е|йс)[а-я]{0,19}", "гои_"),
    _.replaceAll("(а|А|A)(мерик|meric)([а-я]|[a-z]){1,19}", "США"),
    _.replaceAll("Путин[а-я]{0,10}", "Путин"),
    _.replaceAll("Холмс[а-я]", "Холмс"),
    _.replaceAll("(и|И)(з|З)раил[а-я]{1,19}", "Израиль"),
    _.replaceAll("(русс|РУСС|Русс)([а-я]|[А-Я]){1,19}", "русский"))

  def adjust[A, B](m: Map[A, B], k: A, DefaultValue: B)(f: B => B): Map[A, B] = m.updated(k, f(m.getOrElse(k, DefaultValue)))

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    if (args.length != 2) return Future.failed(new IllegalArgumentException("please provide path for the file to be read and the int describing sync/async proc"))
    val fileName = args(0)
    val bSync = args(1).toInt
    val p = Paths.get(fileName)
    if (Files.notExists(p)) {
      logger.error("file {} not found", fileName)
      as.terminate()
      return Future.failed(new FileNotFoundException(p.toAbsolutePath.toString))
    }
    val amendFunc: String => String = chain(amendList)
    if (bSync == 1) syncProcessing(p, amendFunc)
    else if (bSync == 2) readOnly(p)
    else if (bSync == 3) Future(syncProcessingBetterFiles(p,amendFunc))
    else aSyncProcessing(p, amendFunc)
  }

  def syncProcessing(p: Path, amendFunc: String => String)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger) = {
    val lineByLineSource = FileIO.fromPath(p)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 32568, allowTruncation = true))
      .map(_.utf8String).map(_.trim).filter(_.length > 0)
      .map(amendFunc)
    lineByLineSource.filter(_.nonEmpty).flatMapConcat(l => Source(l.split("\\s").toList)
    )
      .fold(Map.empty[String, Int])((l: Map[String, Int], r: String)
      => adjust(l, r, 0)(_ + 1)
      ).map(m => {
      val result = m.filter(p => (p._1.length > 3 || (p._1.toUpperCase == p._1 && p._1.length == 3)) && p._2 > 14).toList.sortWith(_._2 > _._2)
      ByteString(result.toString() + "\n")
    }
    )
      .alsoToMat(FileIO.toPath(Paths.get("tmp/results.txt")))(Keep.both)
      .runForeach { b =>
        logger.warn("sync: {}", b.utf8String)
      }
  }
  def syncProcessingBetterFiles(p: Path, amendFunc: String => String)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger) = {
    val lineByLineSource = ScalaFile(p).lineIterator(Charset.forName("UTF-8"))
      .map(_.trim).filter(_.length > 0)
      .map(amendFunc)
    val stats = lineByLineSource.filter(_.nonEmpty).flatMap(_.split("\\s").toList)
      .foldLeft(Map.empty[String, Int])((l: Map[String, Int], r: String)
      => adjust(l, r, 0)(_ + 1)
      ).filter(p => (p._1.length > 3 || (p._1.toUpperCase == p._1 && p._1.length == 3)) && p._2 > 14).toList.sortWith(_._2 > _._2)
    ScalaFile(Paths.get("tmp/results.txt")).write(stats.toString())
    logger.warn("sync b f: {}", stats)
  }


  def aSyncProcessing(p: Path, amendFunc: String => String)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger) = {
    val random = new Random()
    FileIO.fromPath(p)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 32568, allowTruncation = true))
      .map(_.utf8String).map(_.trim).filter(_.length > 0)
      .groupBy(8, _ => random.nextInt(8))
      //      .mapList(
      .map(amendFunc)
      .filter(_.toString.nonEmpty).flatMapConcat(l => Source(l.toString.split("\\s").toList))
      .fold(Map.empty[String, Int])((l: Map[String, Int], r: String)
      => adjust(l, r, 0)(_ + 1)
      )
      .async
      .mergeSubstreams
      .fold(Map.empty[String, Int])((l: Map[String, Int], substreamMap: Map[String, Int]) => {
        l ++ substreamMap.map { case (k, v) => k -> (v + l.getOrElse(k, 0)) }
      }).map(
      _.filter(p => (p._1.length > 3 || (p._1.toUpperCase == p._1 && p._1.length == 3)) && p._2 > 9)
        .toList
        .sortWith(_._2 > _._2)
    ).map(m => ByteString(m.toString() + "\n"))
      .alsoToMat(FileIO.toPath(Paths.get("tmp/results.txt")))(Keep.both)
      .runForeach { (b: ByteString) =>
        logger.warn("aSync: {}", b.utf8String)
      }
  }

  def chain[T](listofFunc: List[T ⇒ T]): T => T =
    listofFunc.fold(listofFunc.head)((current, next) => current.andThen(next))

  def readOnly(p: Path)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger) = {
    val fut = FileIO.fromPath(p)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 32568, allowTruncation = true))
      .map(_.utf8String).map(_.trim).filter(_.length > 0).zipWithIndex.runWith(Sink.last)
    fut.onComplete { case Success(r) => logger.warn("reading done, last elem {}", r) }
    fut
  }

  //  not used
  implicit class Util[T, U, V, W](flow: SubFlow[T, U, Source[+?, U], V]) {

    def mapList(listofFunc: List[T ⇒ T]): SubFlow[T, U, Source[+?, U], V] =
      flow.map(listofFunc.fold(listofFunc.head)((current, next) => current.andThen(next)))
  }

}
