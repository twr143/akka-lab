package colin
import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit._
object UniformRandomSpin {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val random = new Random()

  def main(args: Array[String]): Unit = {
    runAndComplete(parallelMapAsyncAsync, "uniformRandomSpin", toComplete = false)
    runAndComplete(parallelMapAsyncAsyncUnordered, "parallelMapAsyncAsyncUnordered", toComplete = false)
  }

  def runAndComplete(f: () => Future[Done], name: String, toComplete: Boolean): Unit = {
    val start = System.currentTimeMillis()
    f().onComplete {
      case Success(x) =>
        println(s"$name successfully completed in: ${System.currentTimeMillis() - start}")
        if (toComplete) system.terminate()
      case Failure(e) =>
        println(s"Failure: ${e.getMessage}")
        system.terminate()
    }
  }

  def uniformRandomSpin(value: Int): Future[Int] = Future {
    val max = random.nextInt(6)
    val start = System.currentTimeMillis()
    while ((System.currentTimeMillis() - start) < max) {}
    value
  }

  def nonBlockingCall(value: Int): Future[Int] = {
    val promise = Promise[Int]
    val max = FiniteDuration(random.nextInt(6), MILLISECONDS)
    system.scheduler.scheduleOnce(max) {
      promise.success(value)
    }
    promise.future
  }

  def consequtiveMapAsync(): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .runWith(Sink.ignore)
  }

  def consequtiveMapAsyncAsync(): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1)(uniformRandomSpin).async
      .mapAsync(1)(uniformRandomSpin).async
      .mapAsync(1)(uniformRandomSpin).async
      .mapAsync(1)(uniformRandomSpin).async
      .runWith(Sink.ignore)
  }

  def parallelMapAsyncAsync(): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(8)(uniformRandomSpin).async
      .runWith(Sink.ignore)
  }
  def parallelMapAsyncAsyncUnordered(): Future[Done] = {
    Source(1 to 1000)
      .mapAsyncUnordered(8)(uniformRandomSpin).async
      .runWith(Sink.ignore)
  }


  def parallelNonBlockingCall(): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1000)(nonBlockingCall).async
      .runWith(Sink.ignore)
  }
}
