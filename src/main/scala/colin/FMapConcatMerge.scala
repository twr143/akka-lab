package colin
import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import util.StreamWrapperApp
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}
object FMapConcatMerge extends StreamWrapperApp {
  val random = new Random()

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    val strings = List(
      """hello
         world
         test
         this""",
      """foo
         bar
         baz""",
      """foo
         bar
         baz"""
    )
    for {
      _ <- mapConcat(strings)
      _ <- fMapConcat(strings)
      r <- fMapMerge(strings)
    } yield r
  }

  def mapConcat(strings: List[String])(implicit mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    for {
      _ <- Future {
        println("started mc")
      }
      f <- Source(strings).
        mapConcat(_.split("\n").toList).map(_.trim).
        runForeach(println)
      _ <- Future {
        println("completed mc")
      }
    } yield f
  }

  def fMapConcat(strings: List[String])(implicit mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    for {
      _ <- Future {
        println("started fmc")
      }
      f <- Source(strings).
        flatMapConcat(line => Source(line.split("\n").toList)).map(_.trim).
        runForeach(println)
      _ <- Future {
        println("completed fmc")
      }
    } yield f
  }

  def fMapMerge(strings: List[String])(implicit mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    for {
      _ <- Future {
        println("started fmm")
      }
      f <- Source(strings).
        flatMapMerge(3, line => Source(line.split("\n").toList)).map(_.trim).
        runForeach(println)
      _ <- Future {
        println("completed fmm")
      }
    } yield f
  }
}
