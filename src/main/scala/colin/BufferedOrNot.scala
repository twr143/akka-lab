package colin
import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Random, Success}
object BufferedOrNot {
  val random = new Random()
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  def main(args: Array[String]): Unit = {
      runAndComplete(sequential,"sequential")
      runAndComplete(sequentialWithBuffer,"sequentialWithBuffer")
  }
  def runAndComplete(f: () => Future[Done], name: String, toComplete: Boolean= false): Unit = {
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

  def sequentialWithBuffer(): Future[Done] = {
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
  def sequential(): Future[Done] = {
    Source(1 to 1000)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .mapAsync(1)(uniformRandomSpin)
      .runWith(Sink.ignore)
  }


}
