package streaming.partition
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{GraphDSL, Partition, RunnableGraph, Sink, Source}
import scala.io.StdIn
/**
  * Created by Ilya Volynin on 15.06.2018 at 16:45.
  */
object EvenOdd extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  val numbers = Source[Int](List(1, 2, 3, 4, 5, 6))
  val oddNumbers = Sink.foreach[Int](number => println(s"odd \t$number"))
  val evenNumbers = Sink.foreach[Int](number => println(s"even \t$number"))

  RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    val partition = b.add(Partition[Int](2, number => if (number % 2 == 0) 1 else 0))
    numbers ~> partition.in
    partition.out(0) ~> oddNumbers
    partition.out(1) ~> evenNumbers
    ClosedShape
  }).run()
  Thread.sleep(100)
  system.terminate()
}
