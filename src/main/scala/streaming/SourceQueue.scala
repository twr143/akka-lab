package streaming
import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult, ThrottleMode}
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.concurrent.duration._
import scala.util.{Failure, Success}
/**
  * Created by Ilya Volynin on 14.06.2018 at 22:58.
  */
object SourceQueue extends App {
  implicit val system = ActorSystem("example")
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()
  val bufferSize = 200
  // try throttle
  val queue = Source.queue[Int](bufferSize, OverflowStrategy.backpressure).throttle(10, 1.second, 10, ThrottleMode.shaping)
    .toMat(Sink.foreach {
      println
    })(Keep.left)
    .run()
  for (i ← 1 to 60) {
    queue.offer(i).onComplete {
      case Success(QueueOfferResult.Enqueued) =>
            println(s"$i has been enqueued")
      case Success(QueueOfferResult.Dropped) =>
            println(s"$i has been dropped")
      case Failure(e) =>
        println(s"Failure $i: ${e.getMessage}")
//        system.terminate()
    }
  }
  queue.watchCompletion().onComplete {
    case Success(Done) =>
      println("Stream finished successfully.")
      system.terminate()
    case Failure(e) =>
      println(s"Stream failed with $e")
  }
  Thread.sleep(10000)
  queue.complete()
}
