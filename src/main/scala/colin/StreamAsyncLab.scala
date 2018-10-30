package colin
import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import util.StreamWrapperApp

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
object StreamAsyncLab extends StreamWrapperApp {

  def timeCheckWrapper(f: () => Future[Done], name: String)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    val start = System.currentTimeMillis()
    val res = f()
    res.onComplete {
      case Success(x) =>
        println(s"$name completed for: ${System.currentTimeMillis() - start}")
      case Failure(e) =>
        println(s"Failure: ${e.getMessage}")
        as.terminate()
    }
    res
  }

  def spinSimple()(implicit mat: ActorMaterializer, ec: ExecutionContext): Future[Done] =
    Source(1 to 1000)
      .map(spin)
      .map(spin)
      .runWith(Sink.ignore)

  def spinSimpleAsync()(implicit mat: ActorMaterializer, ec: ExecutionContext): Future[Done] =
    Source(1 to 1000)
      .map(spin)
      .async
      .map(spin)
      .runWith(Sink.ignore)

  def spinMapAsync4()(implicit mat: ActorMaterializer, ec: ExecutionContext): Future[Done] =
    Source(1 to 1000)
      .mapAsync(4)(x => Future(spin(x)))
      .mapAsync(4)(x => Future(spin(x)))
      .runWith(Sink.ignore)

  def spinMapAsync8()(implicit mat: ActorMaterializer, ec: ExecutionContext): Future[Done] =
    Source(1 to 1000)
      .mapAsync(8)(x => Future(spin(x)))
      .mapAsync(8)(x => Future(spin(x)))
      .runWith(Sink.ignore)

  def spin(value: Int)(implicit ec: ExecutionContext): Int = {
    val start = System.currentTimeMillis()
    while ((System.currentTimeMillis() - start) < 2) {}
    value
  }

  override def body()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    Future.sequence(List(timeCheckWrapper(spinSimple, "spinSimple"),
      timeCheckWrapper(spinSimpleAsync, "spinSimpleAsync"),
      timeCheckWrapper(spinMapAsync4, "spinMapAsync4"),
      timeCheckWrapper(spinMapAsync8, "spinMapAsync8")))
  }
}
