package kafka.ser_on.jsoniter
import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.{ActorAttributes, ActorMaterializer, Supervision}
import akka.stream.scaladsl.{Keep, Sink}
import ch.qos.logback.classic.Logger
import com.github.plokhotnyuk.jsoniter_scala.core.JsonParseException
import com.typesafe.config.Config
import kafka.ser_on.jsoniter.Model._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonParseException, readFromArray, writeToArray}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * Created by Ilya Volynin on 13.09.2018 at 14:59.
  */
object ConsumerSer extends KafkaConsumerStreamWrapper {

  // #atLeastOnceBatch
  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger, config: Config): DrainingControl[Done] = {
    val topicName = args(0)
    val consumerSettings =
      ConsumerSettings(config, new StringDeserializer,
        new ByteArrayDeserializer)
        .withBootstrapServers("localhost:9092")
        .withGroupId("group2")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    Consumer
      .committableSource(consumerSettings, Subscriptions.assignmentWithOffset(new TopicPartition(topicName, /* partition = */ 0), 0))
      .mapAsync(2) {
        msg: CommittableMessage[String, Array[Byte]] =>
          readFromArray[SerializationBeanJsoniterBase](msg.record.value) match {
            case v: SerializationBeanJsoniter =>
              business(msg.record.key, v)
                .map(_ => msg.committableOffset)
            case v: SerializationBeanJsoniter2 =>
              business2(msg.record.key, v)
                .map(_ => msg.committableOffset)
            case v =>
              logger.warn("unexpected record {}, skipping", v)
              Future.successful(msg.committableOffset)
          }
      }
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .batch(
        max = 10,
        CommittableOffsetBatch.apply
      )(_.updated(_))
      .mapAsync(3)(_.commitScaladsl())
      .toMat(Sink.ignore)(Keep.both)
      .mapMaterializedValue(DrainingControl.apply)
      .run()
  }

  def business(key: String, value: SerializationBeanJsoniter)(implicit logger: Logger): Future[Done] = {
    logger.warn("k {} v1 {}, {}", key, value, Thread.currentThread().getId.toString)
    if ("7".equals(value)) Future.failed(new Exception("7 is the unlucky number")) else Future.successful(Done)
  }

  def business2(key: String, value: SerializationBeanJsoniter2)(implicit logger: Logger): Future[Done] = {
    logger.warn("k {} v2 {}, {}", key, value, Thread.currentThread().getId.toString)
    if ("7".equals(value)) Future.failed(new Exception("7 is the unlucky number")) else Future.successful(Done)
  }

  def decider(implicit logger: Logger): Supervision.Decider = {
    case e1: JsonParseException ⇒
      logger.warn("base type isn't matched, skipping. {}", e1.getMessage)
      Supervision.Resume
    case NonFatal(e) ⇒
      logger.error(e.getMessage, e)
      Supervision.Stop
  }
}
