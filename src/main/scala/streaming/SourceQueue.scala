package streaming
import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import util.StreamWrapperApp
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by Ilya Volynin on 14.06.2018 at 22:58.
  */
object SourceQueue extends StreamWrapperApp {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    val bufferSize = 200
    val source = Source.queue[Int](bufferSize, OverflowStrategy.backpressure)
    val flowThrottle = Flow[Int].throttle(10, 1.second, 10, ThrottleMode.shaping)
    // try throttle
    val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val workerCount = 2
      val partition = b.add(Partition[Int](workerCount, _ % workerCount))
      val merge = b.add(Merge[Int](workerCount))
      partition ~> Flow[Int]/*.throttle(10, 1.second, 10, ThrottleMode.shaping)*/.map(i => {
        println(s"p:1,i:\t$i\t${Thread.currentThread().getId}")
        i
      }) ~> merge
      partition ~> Flow[Int]/*.throttle(10, 1.second, 10, ThrottleMode.shaping)*/.map(i => {
        println(s"p:2,i:\t$i\t${Thread.currentThread().getId}")
        i
      }) ~> merge
      FlowShape(partition.in, merge.out)
    })
    val (queue, done) = source.via(flow)
      .toMat(Sink.ignore)(Keep.both)
      .run()
    for (i ← 1 to 60) {
      queue.offer(i).onComplete {
        case Success(QueueOfferResult.Enqueued) =>
        //        println(s"$i has been enqueued")
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
      case Failure(e) =>
        println(s"Stream failed with $e")
    }
    Thread.sleep(2400)
    queue.complete()
    done
  }
}
