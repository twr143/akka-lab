package streaming.partition
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random._
import scala.math.abs
import scala.util.Success

/**
  * Created by Ilya Volynin on 15.06.2018 at 18:38.
  */
object StatefulMapConcatTest extends StreamWrapperApp2 {

  case class IdentValue(id: Int, value: String)

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val identValues1 = List.fill(5)(IdentValue(abs(nextInt()) % 5, "valueHere1"))
    val identValues2 = List.fill(5)(IdentValue(abs(nextInt()) % 5, "valueHere2"))
    var ids = Set.empty[Int]
    val stateFlow = Flow[IdentValue].statefulMapConcat { () => // toggle with mapConcat
      //state with already processed ids
      logger.warn("initial block in smc") // printed twice in stateful version, once in mapConcat
      identValue =>
        ids = ids + identValue.id
        Set(identValue)
    }
    val f = Source(identValues1)
      .via(stateFlow)
      .runWith(Sink.seq)
    .andThen { case Success(identValue) => logger.warn(identValue.toString()); logger.warn(s"ids=$ids") }
    val g = Source(identValues2)
      .via(stateFlow)
      .runWith(Sink.seq)
      .andThen { case Success(identValue) => logger.warn(identValue.toString()); logger.warn(s"ids=$ids") }
    Future.sequence(List(f, g))
  }
}
