package flow
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Partition, RunnableGraph, Sink, Source}
import util.StreamWrapperApp
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by Ilya Volynin on 17.11.2018 at 17:21.
  */
object ScanEntry2 extends StreamWrapperApp {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    val someNumbers =
      Source.fromIterator(() => Iterator.from(1))
        .take(6)
    val flow =
      Flow[Int]
        .zipWith(Source.fromIterator(() => Iterator.from(1))) {
          (ori, idx) => (ori, idx)
        }
        .scan((0, 0))((aggr, array) => (array._1, aggr._2 + array._2))
    someNumbers.via(flow).runWith(Sink.foreach(println))
  }
}
