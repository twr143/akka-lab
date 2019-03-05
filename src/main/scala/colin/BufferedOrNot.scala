package colin
import akka.Done
import akka.actor.ActorSystem
import akka.pattern.Patterns.after
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Random, Success}

object BufferedOrNot extends StreamWrapperApp2 {

  val random = new Random()

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    Future.sequence(
      List(runAndComplete(sequential, "sequential"),
        runAndComplete(sequentialWithBuffer, "sequentialWithBuffer")))
  }

  def runAndComplete(f: () => Future[Done], name: String, toComplete: Boolean = false)
                    (implicit as: ActorSystem, ec: ExecutionContext, logger: Logger): Future[Done] = {
    val start = System.currentTimeMillis()
    val res = f()
    res.onComplete {
      case Success(x) =>
        logger.warn(s"$name successfully completed in: ${System.currentTimeMillis() - start}")
      case Failure(e) =>
        logger.warn(s"Failure: ${e.getMessage}")
        as.terminate()
    }
    res
  }

  def uniformRandomSpin(value: Int)(implicit as: ActorSystem, ec: ExecutionContext): Future[Int] = Future {
    val max = random.nextInt(6)
    // doesn't work, too long call
    // after(max.millis, as.scheduler, ec, Future(value))
    val start = System.currentTimeMillis()
    Thread.sleep(System.currentTimeMillis() - start + max)
    value
  }

  def sequentialWithBuffer()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1)(uniformRandomSpin)
      .buffer(16, OverflowStrategy.backpressure)
      .mapAsync(1)(uniformRandomSpin)
      .buffer(16, OverflowStrategy.backpressure)
      .mapAsync(1)(uniformRandomSpin)
      .buffer(16, OverflowStrategy.backpressure)
      .mapAsync(1)(uniformRandomSpin)
      .buffer(16, OverflowStrategy.backpressure)
      .runWith(Sink.ignore)
  }

  def sequential()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .runWith(Sink.ignore)
  }
}
