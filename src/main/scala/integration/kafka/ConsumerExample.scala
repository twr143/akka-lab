package integration.kafka

import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffsetBatch}
import akka.kafka._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RestartSource, Sink, Source}
import akka.stream.{ActorMaterializer, DelayOverflowStrategy}
import akka.{Done, NotUsed}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.{Metric, MetricName, TopicPartition}
import org.apache.kafka.common.serialization._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import java.util.concurrent.atomic.AtomicLong
import akka.kafka.scaladsl.Consumer.DrainingControl
trait ConsumerExample {
  val system = ActorSystem("example")
  implicit val ec = system.dispatcher
  implicit val m = ActorMaterializer.create(system)
  val maxPartitions = 100
  // #settings
  val config = system.settings.config.getConfig("akka.kafka.consumer")
  val consumerSettings =
    ConsumerSettings(config, new StringDeserializer, new /*ByteArrayDeserializer*/ StringDeserializer)
      .withBootstrapServers("localhost:9092")
      .withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
  //#settings
  val consumerSettingsWithAutoCommit =
  // #settings-autocommit
    consumerSettings
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
      .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
  // #settings-autocommit
  val producerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  def business[T] = Flow[T]

  def businessLogic(record: ConsumerRecord[String, String]): Future[Done] = ???

  def terminateWhenDone(result: Future[Done]): Unit =
    result.onComplete {
      case Failure(e) =>
        system.log.error(e, e.getMessage)
        system.terminate()
      case Success(_) => system.terminate()
    }
}
// Consume messages and store a representation, including offset, in DB
object ExternalOffsetStorageExample extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // format: off
    // #plainSource
    val db = new OffsetStore
    val control = db.loadOffset().map { fromOffset =>
      Consumer
        .plainSource(
          consumerSettings,
          Subscriptions.assignmentWithOffset(
            new TopicPartition("testT4", /* partition = */ 0) -> fromOffset
          )
        )
        .mapAsync(1)(db.businessLogicAndStoreOffset)
        .to(Sink.ignore)
        .run()
    }
    // #plainSource
    Thread.sleep(3000)
    control.foreach(c => terminateWhenDone(c.shutdown()))
  }
  // #plainSource
  class OffsetStore {
    // #plainSource
    private val offset = new AtomicLong

    // #plainSource
    def businessLogicAndStoreOffset(record: ConsumerRecord[String, String]): Future[Done] = // ...
    // #plainSource
    {
      println(s"DB.save: ${record.value}")
      offset.set(record.offset)
      Future.successful(Done)
    }

    // #plainSource
    def loadOffset(): Future[Long] = // ...
    // #plainSource
      Future.successful(offset.get)

    // #plainSource
  }
  // #plainSource
  // format: on
}
// Consume messages at-most-once
object AtMostOnceExample extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #atMostOnce
    val control =
      Consumer
        .atMostOnceSource(consumerSettings, Subscriptions.topics("testT6"))
        .mapAsync(1)(record => business(record.key, record.value()))
        .to(Sink.ignore)
        .run()
    // #atMostOnce
    Thread.sleep(2000)
    terminateWhenDone(control.shutdown())
  }

  // #atMostOnce
  def business(key: String, value: String): Future[Done] = {
    println(s"DB.save: $value")
    Future.successful(Done)
  }

  // #atMostOnce
}
// Consume messages at-least-once
object AtLeastOnceExample extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #atLeastOnce
    val control =
      Consumer
        .committableSource(consumerSettings, Subscriptions.assignmentWithOffset(new TopicPartition("testT5", /* partition = */ 0), 0))
        .mapAsync(10) { msg =>
          business(msg.record.key, msg.record.value).map(_ => msg.committableOffset)
        }
        .mapAsync(5)(offset => offset.commitScaladsl())
        .toMat(Sink.ignore)(Keep.both)
        .mapMaterializedValue(DrainingControl.apply)
        .run()
    // #atLeastOnce
    Thread.sleep(3000)
    terminateWhenDone(control.drainAndShutdown())
  }

  // format: off
  // #atLeastOnce
  def business(key: String, value: String): Future[Done] = {
    println(s"k $key v $value")
    Future.successful(Done)
  }

  // #atLeastOnce
  // format: on
}
// Consume messages at-least-once, and commit in batches
object AtLeastOnceWithBatchCommitExample extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #atLeastOnceBatch
    val control =
      Consumer
        .committableSource(consumerSettings, Subscriptions.assignmentWithOffset(new TopicPartition("testT5", /* partition = */ 0), 0))
        .mapAsync(1) { msg =>
          business(msg.record.key, msg.record.value).recover { case e: Exception => println(s"exception ${e.getMessage}");Done }
            .map(_ => msg.committableOffset)
        }
        .batch(
          max = 10,
          CommittableOffsetBatch.apply
        )(_.updated(_))
        .mapAsync(3)(_.commitScaladsl())
        .toMat(Sink.ignore)(Keep.both)
        .mapMaterializedValue(DrainingControl.apply)
        .run()
    // #atLeastOnceBatch
    Thread.sleep(3000)
    terminateWhenDone(control.drainAndShutdown())
  }

  def business(key: String, value: String): Future[Done] = {
    println(s"k $key v $value")
    if ("7".equals(value)) Future.failed(new Exception("7 is the unlucky number")) else Future.successful(Done)
  }
}
// Connect a Consumer to Producer
object ConsumerToProducerSinkExample extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    //format: off
    // #consumerToProducerSink
    val control =
    Consumer
      .committableSource(consumerSettings, Subscriptions.topics("topic1", "topic2"))
      .map { msg =>
        ProducerMessage.Message[String, String, ConsumerMessage.CommittableOffset](
          new ProducerRecord("targetTopic", msg.record.value),
          msg.committableOffset
        )
      }
      .toMat(Producer.commitableSink(producerSettings))(Keep.both)
      .mapMaterializedValue(DrainingControl.apply)
      .run()
    // #consumerToProducerSink
    //format: on
    control.drainAndShutdown()
  }
}
// Connect a Consumer to Producer
object ConsumerToProducerFlowExample extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #consumerToProducerFlow
    val control = Consumer
      .committableSource(consumerSettings, Subscriptions.topics("topic1"))
      .map { msg =>
        ProducerMessage.Message[String, String, ConsumerMessage.CommittableOffset](
          new ProducerRecord("topic2", msg.record.value),
          passThrough = msg.committableOffset
        )
      }
      .via(Producer.flexiFlow(producerSettings))
      .mapAsync(producerSettings.parallelism) { result =>
        val committable = result.passThrough
        committable.commitScaladsl()
      }
      .toMat(Sink.ignore)(Keep.both)
      .mapMaterializedValue(DrainingControl.apply)
      .run()
    // #consumerToProducerFlow
    terminateWhenDone(control.drainAndShutdown())
  }
}
// Connect a Consumer to Producer, and commit in batches
object ConsumerToProducerWithBatchCommitsExample extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #consumerToProducerFlowBatch
    val control = Consumer
      .committableSource(consumerSettings, Subscriptions.topics("testT7"))
      .map(
        msg => {
         // println(s"msg.recod.value = ${msg.record.value}")
          ProducerMessage.Message[String, String, ConsumerMessage.CommittableOffset](
            new ProducerRecord("testT8", msg.record.value),
            msg.committableOffset
          )
        }
      )

      .via(Producer.flexiFlow(producerSettings))

      .map(_.passThrough)
      .batch(max = 5, CommittableOffsetBatch.apply)(_.updated(_))
      .mapAsync(3)(_.commitScaladsl())
      .toMat(Sink.foreach(println(_)))(Keep.both)
      .mapMaterializedValue(DrainingControl.apply)
      .run()
    // #consumerToProducerFlowBatch
    Thread.sleep(3000)
    terminateWhenDone(control.drainAndShutdown())
  }
}
// Connect a Consumer to Producer, and commit in batches
object ConsumerToProducerWithBatchCommits2Example extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    val source = Consumer
      .committableSource(consumerSettings, Subscriptions.topics("topic1"))
      .map(
        msg =>
          ProducerMessage.Message(
            new ProducerRecord[String, String]("topic2", msg.record.value),
            msg.committableOffset
          )
      )
      .via(Producer.flexiFlow(producerSettings))
      .map(_.passThrough)
    val done =
    // #groupedWithin
      source
        .groupedWithin(10, 5.seconds)
        .map(CommittableOffsetBatch(_))
        .mapAsync(3)(_.commitScaladsl())
        // #groupedWithin
        .runWith(Sink.ignore)
    terminateWhenDone(done)
  }
}
// Backpressure per partition with batch commit
object ConsumerWithPerPartitionBackpressure extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #committablePartitionedSource
    val control = Consumer
      .committablePartitionedSource(consumerSettings, Subscriptions.topics("testT6"))
      .flatMapMerge(maxPartitions, _._2)
      .via(business2)
      .map(_.committableOffset)
      .batch(max = 5, CommittableOffsetBatch.apply)(_.updated(_))
      .mapAsync(3)(_.commitScaladsl())
      .to(Sink.ignore)
      .run()
    // #committablePartitionedSource
    Thread.sleep(3000)
    terminateWhenDone(control.shutdown())
  }
  def business2 = Flow[CommittableMessage[String,String]].map{t => println(s"value=${t.record.value()}");t}

}
// Flow per partition
object ConsumerWithIndependentFlowsPerPartition extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    //Process each assigned partition separately
    // #committablePartitionedSource-stream-per-partition
    val control = Consumer
      .committablePartitionedSource(consumerSettings, Subscriptions.topics("topic1"))
      .map {
        case (topicPartition, source) =>
          source
            .via(business)
            .mapAsync(1)(_.committableOffset.commitScaladsl())
            .runWith(Sink.ignore)
      }
      .mapAsyncUnordered(maxPartitions)(identity)
      .to(Sink.ignore)
      .run()
    // #committablePartitionedSource-stream-per-partition
    terminateWhenDone(control.shutdown())
  }
}
// Join flows based on automatically assigned partitions
object ConsumerWithOtherSource extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #committablePartitionedSource3
    type Msg = CommittableMessage[String, String]

    def zipper(left: Source[Msg, _], right: Source[Msg, _]): Source[(Msg, Msg), NotUsed] = ???

    Consumer
      .committablePartitionedSource(consumerSettings, Subscriptions.topics("topic1"))
      .map {
        case (topicPartition, source) =>
          // get corresponding partition from other topic
          val otherTopicPartition = new TopicPartition("otherTopic", topicPartition.partition())
          val otherSource = Consumer.committableSource(consumerSettings, Subscriptions.assignment(otherTopicPartition))
          zipper(source, otherSource)
      }
      .flatMapMerge(maxPartitions, identity)
      .via(business)
      //build commit offsets
      .batch(
      max = 20,
      seed = {
        case (left, right) =>
          (
            CommittableOffsetBatch(left.committableOffset),
            CommittableOffsetBatch(right.committableOffset)
          )
      }
    )(
      aggregate = {
        case ((batchL, batchR), (l, r)) =>
          batchL.updated(l.committableOffset)
          batchR.updated(r.committableOffset)
          (batchL, batchR)
      }
    )
      .mapAsync(1) { case (l, r) => l.commitScaladsl().map(_ => r) }
      .mapAsync(1)(_.commitScaladsl())
      .runWith(Sink.ignore)
    // #committablePartitionedSource3
  }
}
//externally controlled kafka consumer
object ExternallyControlledKafkaConsumer extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #consumerActor

      // несколько вручную назначенных топик-партиций используют единый consumer
    //Consumer is represented by actor
    val consumer: ActorRef = system.actorOf(KafkaConsumerActor.props(consumerSettings))
    //Manually assign topic partition to it
    val controlPartition1 = Consumer
      .plainExternalSource[String, String](
      consumer,
      Subscriptions.assignment(new TopicPartition("topic1", 1))
    )
      .via(business)
      .to(Sink.ignore)
      .run()
    //Manually assign another topic partition
    val controlPartition2 = Consumer
      .plainExternalSource[String, String](
      consumer,
      Subscriptions.assignment(new TopicPartition("topic1", 2))
    )
      .via(business)
      .to(Sink.ignore)
      .run()
    consumer ! KafkaConsumerActor.Stop
    // #consumerActor
    terminateWhenDone(controlPartition1.shutdown().flatMap(_ => controlPartition2.shutdown()))
  }
}
object ConsumerMetrics extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #consumerMetrics
    val control: Consumer.Control = Consumer
      .plainSource(consumerSettings, Subscriptions.assignment(new TopicPartition("topic1", 1)))
      .via(business)
      .to(Sink.ignore)
      .run()
    val metrics: Future[Map[MetricName, Metric]] = control.metrics
    metrics.foreach(map => println(s"metrics: ${map}"))
    // #consumerMetrics
  }
}
class RestartingStream extends ConsumerExample {
  def createStream(): Unit =
  //#restartSource
    RestartSource
      .withBackoff(
        minBackoff = 3.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2
      ) { () =>
        Source.fromFuture {
          val source = Consumer.plainSource(consumerSettings, Subscriptions.topics("topic1"))
          source
            .via(business)
            .watchTermination() {
              case (consumerControl, futureDone) =>
                futureDone
                  .flatMap { _ =>
                    consumerControl.shutdown()
                  }
                  .recoverWith { case _ => consumerControl.shutdown() }
            }
            .runWith(Sink.ignore)
        }
      }
      .runWith(Sink.ignore)

  //#restartSource
}
object RebalanceListenerExample extends ConsumerExample {
  //#withRebalanceListenerActor
  import akka.kafka.TopicPartitionsAssigned
  import akka.kafka.TopicPartitionsRevoked
  class RebalanceListener extends Actor with ActorLogging {
    def receive: Receive = {
      case TopicPartitionsAssigned(sub, assigned) ⇒
        log.info("Assigned: {}", assigned)
      case TopicPartitionsRevoked(sub, revoked) ⇒
        log.info("Revoked: {}", revoked)
    }
  }
  //#withRebalanceListenerActor
  def createActor(implicit system: ActorSystem): Source[ConsumerRecord[String, String], Consumer.Control] = {
    //#withRebalanceListenerActor
    val rebalanceListener = system.actorOf(Props[RebalanceListener])
    val subscription = Subscriptions
      .topics(Set("topic"))
      // additionally, pass the actor reference:
      .withRebalanceListener(rebalanceListener)

    // use the subscription as usual:
    Consumer
      .plainSource(consumerSettings, subscription)
    //#withRebalanceListenerActor
  }
}
// Shutdown via Consumer.Control
object ShutdownPlainSourceExample extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    val offset = 123456L
    // #shutdownPlainSource
    val (consumerControl, streamComplete) =
      Consumer
        .plainSource(consumerSettings,
          Subscriptions.assignmentWithOffset(
            new TopicPartition("topic1", 0) -> offset
          ))
        .mapAsync(1)(businessLogic)
        .toMat(Sink.ignore)(Keep.both)
        .run()
    consumerControl.shutdown()
    // #shutdownPlainSource
    terminateWhenDone(streamComplete)
  }
}
// Shutdown when batching commits
object ShutdownCommitableSourceExample extends ConsumerExample {
  def main(args: Array[String]): Unit = {
    // #shutdownCommitableSource
    val drainingControl =
      Consumer
        .committableSource(consumerSettings, Subscriptions.topics("topic1"))
        .mapAsync(1) { msg =>
          businessLogic(msg.record).map(_ => msg.committableOffset)
        }
        .batch(max = 20, first => CommittableOffsetBatch(first)) { (batch, elem) =>
          batch.updated(elem)
        }
        .mapAsync(3)(_.commitScaladsl())
        .toMat(Sink.ignore)(Keep.both)
        .mapMaterializedValue(DrainingControl.apply)
        .run()
    val streamComplete = drainingControl.drainAndShutdown()

    // #shutdownCommitableSource
    terminateWhenDone(streamComplete)
  }
}