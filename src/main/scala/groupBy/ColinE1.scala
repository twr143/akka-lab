package groupBy
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
/**
  * Created by Ilya Volynin on 24.09.2018 at 11:20.
  */
object ColinE1 extends StreamWrapperApp2{
  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    Source(1 to 100)
      .groupBy(4, _ % 4)
      .map { a =>
        logger.error(s"a= $a t id ${Thread.currentThread().getId}")
        a
      }.fold(ListBuffer[Long]()) {
      (s, elem) => s ++ ListBuffer[Long](elem)
    }
      .async
      .mergeSubstreams
      .runForeach {
        seq =>
          logger.warn(s"length = ${seq.length}")
          logger.warn(seq.toString)
      }
  }
}
