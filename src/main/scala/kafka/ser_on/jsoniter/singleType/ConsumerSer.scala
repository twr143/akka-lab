package kafka.ser_on.jsoniter.singleType

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.scaladsl.Consumer
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import ch.qos.logback.classic.Level
import kafka.ser_on.jsoniter.JsoniterScalaSerialization
import kafka.ser_on.jsoniter.Model.{SerializationBeanJsoniter, SerializationBeanJsoniterBase}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}
/**
  * Created by Ilya Volynin on 13.09.2018 at 14:59.
  */
trait ConsumerSer {

  val root = LoggerFactory.getLogger("org.apache.kafka.clients.consumer").asInstanceOf[ch.qos.logback.classic.Logger]
  root.setLevel(Level.WARN)

  val system = ActorSystem("example")
  implicit val ec = system.dispatcher
  implicit val m = ActorMaterializer.create(system)
  val maxPartitions = 100
  // #settings
  val config = system.settings.config.getConfig("akka.kafka.consumer")
  val consumerSettings =
    ConsumerSettings(config, new StringDeserializer, JsoniterScalaSerialization.jsoniterScalaDeserializer[SerializationBeanJsoniterBase]())
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
  import kafka.ser_on.jsoniter.Model._
  def main(args: Array[String]): Unit = {
    // #atLeastOnceBatch
    val control =
      Consumer
        .committableSource(consumerSettings, Subscriptions.assignmentWithOffset(new TopicPartition("testT1", /* partition = */ 0), 0))
        .mapAsync(2) {
          case msg if msg.record.value().isInstanceOf[SerializationBeanJsoniter]  =>
          business(msg.record.key, msg.record.value.asInstanceOf[SerializationBeanJsoniter]).recover { case e: Exception => println(s"exception ${e.getMessage}");Done }
            .map(_ => msg.committableOffset)
          case msg if msg.record.value().isInstanceOf[SerializationBeanJsoniter2]  =>
          business2(msg.record.key, msg.record.value.asInstanceOf[SerializationBeanJsoniter2]).recover { case e: Exception => println(s"exception ${e.getMessage}");Done }
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
    Thread.sleep(30000)
    terminateWhenDone(control.drainAndShutdown())
  }

  def business(key: String, value: SerializationBeanJsoniter): Future[Done] = {
    println(s"k $key v1 $value, ${Thread.currentThread().getId}")
    if ("7".equals(value)) Future.failed(new Exception("7 is the unlucky number")) else Future.successful(Done)
  }
  def business2(key: String, value: SerializationBeanJsoniter2): Future[Done] = {
    println(s"k $key v2 $value, ${Thread.currentThread().getId}")
    if ("7".equals(value)) Future.failed(new Exception("7 is the unlucky number")) else Future.successful(Done)
  }

}
