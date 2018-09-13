package kafka.ser_on.circe

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.scaladsl.Consumer
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import com.ovoenergy.kafka.serialization.circe._
import kafka.ser_on.circe.Model.SerializationBean
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.Future
import scala.util.{Failure, Success}
/**
  * Created by Ilya Volynin on 13.09.2018 at 13:41.
  */
trait ConsumerSer {
  val system = ActorSystem("example")
  implicit val ec = system.dispatcher
  implicit val m = ActorMaterializer.create(system)
  val maxPartitions = 100
  // #settings
  val config = system.settings.config.getConfig("akka.kafka.consumer")
  val consumerSettings =
    ConsumerSettings(config, new StringDeserializer, circeJsonDeserializer[SerializationBean])
      .withBootstrapServers("localhost:9092")
      .withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
  def terminateWhenDone(result: Future[Done]): Unit =
    result.onComplete {
      case Failure(e) =>
        system.log.error(e, e.getMessage)
        system.terminate()
      case Success(_) => system.terminate()
    }


}

object ConsumerSerWithBatchCommitExample extends ConsumerSer {
  def main(args: Array[String]): Unit = {
    // #atLeastOnceBatch
    val control =
      Consumer
        .committableSource(consumerSettings, Subscriptions.assignmentWithOffset(new TopicPartition("testT9", /* partition = */ 0), 10))
        .mapAsync(2) { msg =>
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

  def business(key: String, value: SerializationBean): Future[Done] = {
    println(s"k $key v $value, ${Thread.currentThread().getId}")
    if ("7".equals(value)) Future.failed(new Exception("7 is the unlucky number")) else Future.successful(Done)
  }
}

