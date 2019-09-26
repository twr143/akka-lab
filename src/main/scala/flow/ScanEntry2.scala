package flow
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Partition, RunnableGraph, Sink, Source}
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by Ilya Volynin on 17.11.2018 at 17:21.
  */
object ScanEntry2 extends StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val someNumbers =
      Source.fromIterator(() => Iterator.from(1))
        .take(6)
    val flow =
      Flow[Int]
        .zipWith(Source.fromIterator(() => Iterator.from(1)).map(_ * 2)) {
          (ori, idx) => (ori, idx)
        }
        .scan((0, 0))((aggr, array) => {
          logger.warn(s"aggr: ${aggr.toString()} array[2]: ${array._2}")
          (array._1, aggr._2 + array._2)
        })
    someNumbers.via(flow).runWith(Sink.foreach(a => logger.warn(a.toString())))
  }
}
