package streaming.files
import java.io.FileNotFoundException
import java.nio.file.{Files, Path, Paths}
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing, Keep, Source}
import akka.util.ByteString
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

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
    if (args.length != 2) return Future.failed(new IllegalArgumentException("please provide path for the file to be read and the int describing sync/async proc"))
    val fileName = args(0)
    val bSync = args(1).toInt
    val p = Paths.get(fileName)
    if (Files.notExists(p)) {
      logger.error("file {} not found", fileName)
      as.terminate()
      return Future.failed(new FileNotFoundException(p.toAbsolutePath.toString))
    }
    if (bSync == 1) syncProcessing(p) else aSyncProcessing(p)
  }

  def syncProcessing(p: Path)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger) = {
    val lineByLineSource = FileIO.fromPath(p)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 32568, allowTruncation = true))
      .map(_.utf8String).map(_.trim).filter(_.length > 0)
      .map(_.replaceAll("\"|,|\\.|!|-|\\?", ""))
      .map(_.replaceAll("ожид", "ожИд"))
      .map(_.replaceAll("(НЕ)?(ж|Ж)(и|ы)д[а-я]{1,15}", "жыды"))
      .map(_.replaceAll("Росси.", "Россия"))
      .map(_.replaceAll("(е|Е)вре[а-я]{1,19}", "евреи"))
      .map(_.replaceAll("(г|Г)(о|О)(И|и|йс)[а-я]{0,19}", "гои_"))
      .map(_.replaceAll("(а|А|A)(мерик|meric)([а-я]|[a-z]){1,19}", "США"))
      //      .map(_.replaceAll("СШАо", "США"))
      .map(_.replaceAll("Путин[а-я]{0,10}", "Путин"))
      .map(_.replaceAll("Холмс[а-я]", "Холмс"))
      .map(_.replaceAll("(и|И)(з|З)раил[а-я]{1,19}", "Израиль"))
      .map(_.replaceAll("(русс|РУСС|Русс)([а-я]|[А-Я]){1,19}", "русский"))
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

  def aSyncProcessing(p: Path)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger) = {
    val random = new Random()
    FileIO.fromPath(p)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 32568, allowTruncation = true))
      .map(_.utf8String).map(_.trim).filter(_.length > 0)
      .groupBy(8, _ => random.nextInt(8))
      .map(_.replaceAll("\"|,|\\.|!|-|\\?", ""))
      .map(_.replaceAll("ожид", "ожИд"))
      .map(_.replaceAll("(НЕ)?(ж|Ж)(и|ы)д[а-я]{1,15}", "жыды"))
      .map(_.replaceAll("Росси.", "Россия"))
      .map(_.replaceAll("(е|Е)вре[а-я]{1,19}", "евреи"))
      .map(_.replaceAll("(г|Г)(о|О)(И|и|йс)[а-я]{0,19}", "гои_"))
      .map(_.replaceAll("(а|А|A)(мерик|meric)([а-я]|[a-z]){1,19}", "США"))
      //      .map(_.replaceAll("СШАо", "США"))
      .map(_.replaceAll("Путин[а-я]{0,10}", "Путин"))
      .map(_.replaceAll("Холмс[а-я]", "Холмс"))
      .map(_.replaceAll("(и|И)(з|З)раил[а-я]{1,19}", "Израиль"))
      .map(_.replaceAll("(русс|РУСС|Русс)([а-я]|[А-Я]){1,19}", "русский"))
      .filter(_.nonEmpty).flatMapConcat(l => Source(l.split("\\s").toList))
      .fold(Map.empty[String, Int])((l: Map[String, Int], r: String)
      => adjust(l, r, 0)(_ + 1)
      )
      .async
      .mergeSubstreams
      .fold(Map.empty[String, Int])((l: Map[String, Int], substreamMap: Map[String, Int]) => {
        l ++ substreamMap.map { case (k, v) => k -> (v + l.getOrElse(k, 0)) }
      }).map(m => {
      val result =
        m.filter(p => (p._1.length > 3 || (p._1.toUpperCase == p._1 && p._1.length == 3)) && p._2 > 14)
          .toList
          .sortWith(_._2 > _._2)
      ByteString(result.toString() + "\n")
    }
    )
      .alsoToMat(FileIO.toPath(Paths.get("tmp/results.txt")))(Keep.both)
      .runForeach { b =>
        logger.warn("aSync: {}", b.utf8String)
      }
  }
}
