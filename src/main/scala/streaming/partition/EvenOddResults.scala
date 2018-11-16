package streaming.partition
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Partition, RunnableGraph, Sink, Source}
import util.StreamWrapperApp
import scala.collection.mutable._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by Ilya Volynin on 15.06.2018 at 17:05.
  */
object EvenOddResults extends StreamWrapperApp {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    val numbers = Source[Int](List(1, 2, 3, 4, 5, 6))
    val errCheck = Flow[Int].
      fold(ListBuffer[Int](), ListBuffer[Int]())((lists, number) =>
        if (number % 2 == 0)
          ( {
            lists._1 += number
            lists._1
          }, lists._2)
        else
          (lists._1, {
            lists._2 += number
            lists._2
          }))
    numbers.via(errCheck).runWith(Sink.foreach(s => alertSourceCompleted(s._1, s._2)))
  }

  def alertSourceCompleted(errors: ListBuffer[Int], valids: ListBuffer[Int]): Unit = {
    println(s"errors: $errors")
    println(s"valids: $valids")
  }
}
