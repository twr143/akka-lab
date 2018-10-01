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
import util.StreamWrapperApp

object UniformRandomSpin extends StreamWrapperApp {

  val random = new Random()

  override def body()(implicit as: ActorSystem, mat: ActorMaterializer): Future[Any] = {
    Future.sequence(List(timeCheckWrapper(parallelMapAsyncAsync, "uniformRandomSpin"),
      timeCheckWrapper(parallelMapAsyncAsyncUnordered, "parallelMapAsyncAsyncUnordered"),
      timeCheckWrapper(parallelNonBlockingCall, "parallelNonBlockingCall")))
  }

  def timeCheckWrapper(f: () => Future[Done], name: String)(implicit as: ActorSystem, m: ActorMaterializer): Future[Done] = {
    val start = System.currentTimeMillis()
    val res = f()
    res.onComplete {
      case Success(x) =>
        println(s"$name successfully completed in: ${System.currentTimeMillis() - start}")
      case Failure(e) =>
        println(s"Failure: ${e.getMessage}")
        as.terminate()
    }
    res
  }

  def uniformRandomSpin(value: Int)(implicit as: ActorSystem, mat: ActorMaterializer): Future[Int] = Future {
    val max = random.nextInt(6)
    val start = System.currentTimeMillis()
    while ((System.currentTimeMillis() - start) < max) {}
    value
  }

  def nonBlockingCall(value: Int)(implicit as: ActorSystem, mat: ActorMaterializer): Future[Int] = {
    val promise = Promise[Int]
    val max = FiniteDuration(random.nextInt(6), MILLISECONDS)
    as.scheduler.scheduleOnce(max) {
      promise.success(value)
    }
    promise.future
  }

  def consequtiveMapAsync()(implicit as: ActorSystem, mat: ActorMaterializer): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .runWith(Sink.ignore)
  }

  def consequtiveMapAsyncAsync()(implicit as: ActorSystem, mat: ActorMaterializer): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1)(uniformRandomSpin).async
      .mapAsync(1)(uniformRandomSpin).async
      .mapAsync(1)(uniformRandomSpin).async
      .mapAsync(1)(uniformRandomSpin).async
      .runWith(Sink.ignore)
  }

  def parallelMapAsyncAsync()(implicit as: ActorSystem, mat: ActorMaterializer): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(8)(uniformRandomSpin).async
      .runWith(Sink.ignore)
  }

  def parallelMapAsyncAsyncUnordered()(implicit as: ActorSystem, mat: ActorMaterializer): Future[Done] = {
    Source(1 to 1000)
      .mapAsyncUnordered(8)(uniformRandomSpin).async
      .runWith(Sink.ignore)
  }

  def parallelNonBlockingCall()(implicit as: ActorSystem, mat: ActorMaterializer): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(8)(nonBlockingCall).async
      .runWith(Sink.ignore)
  }
}
