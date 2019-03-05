package streaming.partition
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{GraphDSL, Partition, RunnableGraph, Sink, Source}
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

/**
  * Created by Ilya Volynin on 15.06.2018 at 16:45.
  */
object EvenOdd extends StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val numbers = Source[Int](List(1, 2, 3, 4, 5, 6))
    val oddNumbers = Sink.foreach[Int](number => logger.warn(s"odd \t$number"))
    val evenNumbers = Sink.foreach[Int](number => logger.warn(s"even \t$number"))
    RunnableGraph.fromGraph(GraphDSL.create(oddNumbers, evenNumbers)((a, _) => a) { implicit b =>
      import GraphDSL.Implicits._
      (oddNumbers, evenNumbers) =>
        val partition = b.add(Partition[Int](2, number => (number + 1) % 2))
        numbers ~> partition.in
        partition.out(0) ~> oddNumbers
        partition.out(1) ~> evenNumbers
        ClosedShape
    }).run()
  }
}
