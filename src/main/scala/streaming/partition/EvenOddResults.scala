package streaming.partition
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Partition, RunnableGraph, Sink, Source}
import scala.collection.mutable._
/**
  * Created by Ilya Volynin on 15.06.2018 at 17:05.
  */
object EvenOddResults extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  //global variables - not good
  val errorMap: Map[Int, ListBuffer[Int]] = Map(1 -> ListBuffer[Int]())
  val validMap: Map[Int, ListBuffer[Int]] = Map(1 -> ListBuffer[Int]())
  val numbers = Source[Int](List(1, 2, 3, 4, 5, 6))
  val errCheck = Flow[Int].map(i => {
    if (i % 2 == 0) errorMap.get(1).map(_.+=(i)) else validMap.get(1).map(_.+=(i))
    i
  })
  val afterStreamProcess = Flow[Int].map(i => {
    if (errorMap.get(1).map(_.size).getOrElse(0) +
      validMap.get(1).map(_.size).getOrElse(0) >= 6 /*assume we know total size*/ ) {
      alertSourceCompleted(errorMap.getOrElse(1, ListBuffer[Int]()), validMap.getOrElse(1, ListBuffer[Int]()))
      errorMap -= 1
      validMap -= 1
    }
    i
  })
  RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    numbers ~> errCheck ~> afterStreamProcess ~> Sink.ignore
    ClosedShape
  }).run()
  //numbers.via(errCheck).to(Sink.ignore).run()
  Thread.sleep(100)
  system.terminate()

  def alertSourceCompleted(errors: ListBuffer[Int], valids: ListBuffer[Int]): Unit = {
    println(s"errors: $errors")
    println(s"valids: $valids")
  }
}
