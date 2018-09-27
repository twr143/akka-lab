package streaming
import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
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
  val source = Source.queue[Int](bufferSize, OverflowStrategy.backpressure)
  val flowThrottle = Flow[Int].throttle(10, 1.second, 10, ThrottleMode.shaping)
  // try throttle
  val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val workerCount = 2

    val partition = b.add(Partition[Int](workerCount, _ % workerCount))
    val merge = b.add(Merge[Int](workerCount))

        partition ~> Flow[Int].throttle(10, 1.second, 10, ThrottleMode.shaping).map(i=>{println(s"p:1,i:\t$i");i}) ~> merge
        partition ~> Flow[Int].throttle(10, 1.second, 10, ThrottleMode.shaping).map(i=>{println(s"p:2,i:\t$i");i}) ~> merge

    FlowShape(partition.in, merge.out)
  })
  val queue = source.via(flow)
    .toMat(Sink.ignore)(Keep.left)
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
      system.terminate()
    case Failure(e) =>
      println(s"Stream failed with $e")
      system.terminate()
  }
  Thread.sleep(3000)
  queue.complete()
}
