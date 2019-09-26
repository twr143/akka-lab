package colin
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}
import akka.stream.contrib.Implicits.TimedFlowDsl
import scala.concurrent.duration.FiniteDuration

object FMapConcatMerge extends StreamWrapperApp2 {

  val random = new Random()

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val strings = List(
      """hello
         world
         test
         this""",
      """foo
         bar
         baz""",
      "foo \n bar baz"
    )
    for {
      _ <- mapConcat(strings)
      _ <- fMapConcat(strings)
      r <- fMapMerge(strings)
    } yield r
  }

  def timeCheck(duration: FiniteDuration)(implicit logger: Logger, desc: String): Unit = {
    logger.warn("{} elements passed in {}", desc + " " + 2.toString, duration.toMillis)
  }

  def measureTime(descr: String)(implicit logger: Logger): Flow[String, String, NotUsed] = {
    implicit val desc = descr
    Flow[String].zipWithIndex.timedIntervalBetween(elem => elem._2 == 0 || elem._2 == 2, timeCheck).map(_._1)
  }

  def mapConcat(strings: List[String])(implicit logger: Logger, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    for {
      _ <- Future {
        logger.warn("started mc")
      }
      f <- Source(strings).via(measureTime("mapConcat")).
        mapConcat(_.split("\n").toList).map(_.trim).
        runForeach(logger.warn)
      _ <- Future {
        logger.warn("completed mc")
      }
    } yield f
  }

  def fMapConcat(strings: List[String])(implicit logger: Logger, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    for {
      _ <- Future {
        logger.warn("started fmc")
      }
      f <- Source(strings).via(measureTime("fMapConcat")).
        flatMapConcat(line => Source(line.split("\n").toList)).map(_.trim).
        runForeach(logger.warn)
      _ <- Future {
        logger.warn("completed fmc")
      }
    } yield f
  }

  def fMapMerge(strings: List[String])(implicit logger: Logger, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    for {
      _ <- Future {
        logger.warn("started fmm")
      }
      f <- Source(strings).via(measureTime("fMapMerge")).
        flatMapMerge(3, line => Source(line.split("\n").toList)).map(_.trim).
        runForeach(logger.warn)
      _ <- Future {
        logger.warn("completed fmm")
      }
    } yield f
  }
}
