package windows
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by Ilya Volynin on 13.03.2019 at 9:29.
  */
object SlidingE extends StreamWrapperApp2{

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val someNumbers =
      Source.fromIterator(() => Iterator.from(1))
        .take(6)
    val flow =
      Flow[Int]
        .sliding(2,2)
    someNumbers.via(flow).runWith(Sink.foreach(a => logger.warn(a.toString())))

  }
}
