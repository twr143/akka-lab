package groupBy
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import util.StreamWrapperApp

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random
/**
  * Created by Ilya Volynin on 23.09.2018 at 18:16.
  */
object groupByEntry1 extends StreamWrapperApp {
  override def body()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    Source
      .tick(0 second, 50 millis, "").take(60).
      map {
        _ => if (Random.nextBoolean) (1, s"A") else (2, s"B")
      }
      .groupBy(10, _._1)
      // how to aggregate grouped elements here for two seconds?
      .fold(Seq[String]()) { (x, y) => x ++ Seq(y._2) }
        .async//.filter(_.length % 20 == 0)//.takeWhile(_.length < 100)
      .mergeSubstreams
      .runForeach {
        seq =>
          println(s"length = ${seq.length}")
          println(seq)
      }

  }
}