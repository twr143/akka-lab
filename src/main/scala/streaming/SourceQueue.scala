package streaming
import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by Ilya Volynin on 14.06.2018 at 22:58.
  */
object SourceQueue extends StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val bufferSize = 200
    val source = Source.queue[Int](bufferSize, OverflowStrategy.backpressure)
    val flowThrottle = Flow[Int].throttle(10, 1.second, 10, ThrottleMode.shaping)
    // try throttle
    val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val workerCount = 2
      val partition = b.add(Partition[Int](workerCount, _ % workerCount))
      val merge = b.add(Merge[Int](workerCount))
      partition ~> Flow[Int] /*.throttle(10, 1.second, 10, ThrottleMode.shaping)*/ .map(i => {
        logger.warn(s"p:1,i:\t$i\t${Thread.currentThread().getId}")
        i
      }) ~> merge
      partition ~> Flow[Int] /*.throttle(10, 1.second, 10, ThrottleMode.shaping)*/ .map(i => {
        logger.warn(s"p:2,i:\t$i\t${Thread.currentThread().getId}")
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
          logger.warn(s"$i has been dropped")
        case Failure(e) =>
          logger.warn(s"Failure $i: ${e.getMessage}")
        //        system.terminate()
      }
    }
    queue.watchCompletion().onComplete {
      case Success(Done) =>
        logger.warn("Stream finished successfully.")
      case Failure(e) =>
        logger.warn(s"Stream failed with $e")
    }
    Thread.sleep(2400)
    queue.complete()
    done
  }
}
