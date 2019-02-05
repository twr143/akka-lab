package colin
import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Random, Success}
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit._
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import util.StreamWrapperApp2._

object UniformAsyncUnblockingSpin extends StreamWrapperApp2 {

  val random = new Random()

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    Future.sequence(List(timeCheckWrapper(parallelMapAsyncAsync, "uniformRandomSpin"),
      timeCheckWrapper(parallelMapAsyncAsyncUnordered, "parallelMapAsyncAsyncUnordered"),
      timeCheckWrapper(parallelNonBlockingCall, "parallelNonBlockingCall")))
  }

  def uniformRandomSpin(value: Int)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Int] = Future {
    val max = random.nextInt(6)
    Thread.sleep(max)
    value
  }

  def nonBlockingCall(value: Int)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Int] = {
    val promise = Promise[Int]
    val max = FiniteDuration(random.nextInt(6), MILLISECONDS)
    as.scheduler.scheduleOnce(max) {
      promise.success(value)
    }
    promise.future
  }

  def consequtiveMapAsync()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .runWith(Sink.ignore)
  }

  def consequtiveMapAsyncAsync()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1)(uniformRandomSpin).async
      .mapAsync(1)(uniformRandomSpin).async
      .mapAsync(1)(uniformRandomSpin).async
      .mapAsync(1)(uniformRandomSpin).async
      .runWith(Sink.ignore)
  }

  def parallelMapAsyncAsync()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(8)(uniformRandomSpin).async
      .runWith(Sink.ignore)
  }

  def parallelMapAsyncAsyncUnordered()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    Source(1 to 1000)
      .mapAsyncUnordered(8)(uniformRandomSpin).async
      .runWith(Sink.ignore)
  }

  def parallelNonBlockingCall()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(8)(nonBlockingCall).async
      .runWith(Sink.ignore)
  }
}
