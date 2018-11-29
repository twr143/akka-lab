package streaming.partition
import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Partition, RunnableGraph, Sink, Source}
import akka.stream.scaladsl.GraphDSL.Implicits._
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by Ilya Volynin on 28.11.2018 at 20:15.
  * Many thanks to Stefano Bonetti for his article (and video presentation)
  * https://speakerdeck.com/svezfaz/stream-driven-development-design-your-data-pipeline-with-akka-streams
  */
object Branching extends StreamWrapperApp2 {

  override def body(args: Array[String])
                   (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {

    val numbers = Source[Int](List(1, 2, 3, 4, 5, 6))
    val s1 = Sink.foreach[Int](logger.warn("%3=0: \t{}", _))
    val s2 = Sink.foreach[Int](logger.warn("%3=1: \t{}", _))
    val s3 = Sink.foreach[Int](logger.warn("%3=2: \t{}", _))
    val s4 = Sink.foreach[Int](logger.warn("%60==0: \t{}", _))
    val flow1 = Flow[Int].map(_ * 10).map(errBranchFunc).via(divertErrors(s4))
    numbers.map(err1Func).via(divertErrors[Int, Int, Future[Done]](flow1.toMat(s1)(Keep.right)))
      .map(err2Func).via(divertErrors[Int, Int, Future[Done]](s2)).runWith(s3)

  }

  def err1Func(n: Int): Either[Int, Int] = if (n % 3 == 0) Left(n) else Right(n)

  def err2Func(n: Int): Either[Int, Int] = if (n % 3 == 1) Left(n) else Right(n)

  def errBranchFunc(n: Int): Either[Int, Int] = if (n % 60 == 0) Left(n) else Right(n)

  def divertErrors[T, E, M](to: Sink[E, M]): Flow[Either[E, T], T, M] = {
    Flow.fromGraph(GraphDSL.create(to) {
      implicit b =>
        sink =>
          val partition = b.add(Partition[Either[E, T]](2, _.fold(_ => 0, _ => 1)))
          val left = b.add(Flow[Either[E, T]].map(_.left.get))
          val right = b.add(Flow[Either[E, T]].map(_.right.get))
          partition ~> left ~> sink
          partition ~> right
          FlowShape(partition.in, right.out)
    })
  }
}


