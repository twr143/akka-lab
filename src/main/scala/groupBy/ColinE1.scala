package groupBy
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import util.StreamWrapperApp

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
/**
  * Created by Ilya Volynin on 24.09.2018 at 11:20.
  */
object ColinE1 extends StreamWrapperApp{
  override def body()(implicit as: ActorSystem, mat: ActorMaterializer): Future[Any] = {
    Source(1 to 100)
      .groupBy(4, _ % 4)
      .map { a =>
        val i = Thread.currentThread().getId
        println(s"a= $a t id $i")
        (a, i)
      }.fold(ListBuffer[Long]()) {
      (s, elem) => s ++ ListBuffer(elem._2)
    }
      .async
      .mergeSubstreams
      .runForeach {
        seq =>
          println(s"length = ${seq.length}")
          println(seq)
      }
  }
}
