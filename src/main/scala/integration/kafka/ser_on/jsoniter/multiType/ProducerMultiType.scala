package integration.kafka.ser_on.jsoniter.multiType

/**
  * Created by Ilya Volynin on 14.09.2018 at 14:23.
  */
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ThrottleMode}
import ch.qos.logback.classic.Logger
import com.typesafe.config.Config
import integration.kafka.ser_on.jsoniter.{KafkaProducerStreamWrapper, KafkaSerJsoniter}
import integration.kafka.ser_on.jsoniter.{KafkaProducerStreamWrapper, KafkaSerJsoniter}
import integration.kafka.ser_on.jsoniter.Model.{SerializationBeanJsoniter, SerializationBeanJsoniter2, SerializationBeanJsoniterBase}
import integration.kafka.ser_on.jsoniter.{KafkaProducerStreamWrapper, KafkaSerJsoniter}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import integration.kafka.ser_on.jsoniter.Model._

/**
  * Created by Ilya Volynin on 13.09.2018 at 9:08.
  */
object ProducerMultiType extends KafkaProducerStreamWrapper {

  def body(args: Array[String])
          (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger, config: Config): Future[Done] = {
    val producerSettings =
      ProducerSettings(config, new StringSerializer, KafkaSerJsoniter.jsoniterScalaSerializer()).withBootstrapServers("localhost:9092")

    Source.fromIterator(() => Iterator.range(3200, 3210)).throttle(10, FiniteDuration(1, TimeUnit.SECONDS), 10, ThrottleMode.shaping).
      map {
        case e if e % 2 == 0 => SerializationBeanJsoniter(e.toString, e, OffsetDateTime.now())
        case e if e % 2 == 1 => SerializationBeanJsoniter2(e.toString, e, OffsetDateTime.now(), e)
      }
      .map(value => new ProducerRecord[String, SerializationBeanJsoniterBase]("testT2", value))
      .runWith(Producer.plainSink(producerSettings))
  }



}
