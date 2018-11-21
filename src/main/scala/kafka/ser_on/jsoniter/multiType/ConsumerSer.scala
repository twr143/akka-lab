package kafka.ser_on.jsoniter.multiType
/**
  * Created by Ilya Volynin on 14.09.2018 at 14:23.
  */
import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import ch.qos.logback.classic.{Level, Logger}
import com.typesafe.config.Config
import kafka.ser_on.jsoniter.{JsoniterScalaSerialization, KafkaConsumerStreamWrapper}
import kafka.ser_on.jsoniter.Model.{SerializationBeanJsoniter, SerializationBeanJsoniter2, SerializationBeanJsoniterBase}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * Created by Ilya Volynin on 13.09.2018 at 14:59.
  */
object ConsumerSer extends KafkaConsumerStreamWrapper {

  // #atLeastOnceBatch
  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger, config: Config): DrainingControl[Done] = {
    val consumerSettings =
      ConsumerSettings(config, new StringDeserializer,
        JsoniterScalaSerialization.jsoniterScalaDeserializer[SerializationBeanJsoniterBase]())
        .withBootstrapServers("localhost:9092")
        .withGroupId("group2")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    Consumer
      .committableSource(consumerSettings, Subscriptions.assignmentWithOffset(new TopicPartition("testT2", /* partition = */ 0), 0))
      .mapAsync(2) {
        msg: CommittableMessage[String, SerializationBeanJsoniterBase] =>
          msg.record.value match {
            case v: SerializationBeanJsoniter =>
              business(msg.record.key, v).recover { case NonFatal(e) => logger.error(e.getMessage, e); Done }
                .map(_ => msg.committableOffset)
            case v: SerializationBeanJsoniter2 =>
              business2(msg.record.key, v).recover { case NonFatal(e) => logger.error(e.getMessage, e); Done }
                .map(_ => msg.committableOffset)
          }
      }
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
}