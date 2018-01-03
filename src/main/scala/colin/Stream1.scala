package colin
import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
object Stream1 {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  def main(args: Array[String]): Unit = {
    val start = System.currentTimeMillis()
    spinMapAsync().onComplete {
      case Success(x) =>
        println(s"Successfully completed for: ${System.currentTimeMillis() - start}")
        system.terminate()
      case Failure(e) =>
        println(s"Failure: ${e.getMessage}")
        system.terminate()
    }
  }

  def spinSimple(): Future[Done] =
    Source(1 to 1000)
      .map(spin)
      .map(spin)
      .runWith(Sink.ignore)

  def spinSimpleAsync(): Future[Done] =
    Source(1 to 1000)
      .map(spin)
      .async
      .map(spin)
      .runWith(Sink.ignore)

  def spinMapAsync(): Future[Done] =
    Source(1 to 1000)
      .mapAsync(4)(x => Future(spin(x)))
      .mapAsync(4)(x => Future(spin(x)))
      .runWith(Sink.ignore)

  def spin(value: Int): Int = {
    val start = System.currentTimeMillis()
    while ((System.currentTimeMillis() - start) < 2) {}
    value
  }
}
