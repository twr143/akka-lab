package kafka.ser_on.jsoniter.multiMessage
/**
  * Created by Ilya Volynin on 13.09.2018 at 15:05.
  */
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerMessage.MultiResultPart
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import ch.qos.logback.classic.Logger
import com.typesafe.config.Config
import kafka.ser_on.jsoniter.Model.{SerializationBeanJsoniter, SerializationBeanJsoniter3, SerializationBeanJsoniterBase}
import kafka.ser_on.jsoniter.{KafkaSerJsoniter, KafkaProducerStreamWrapper}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import kafka.ser_on.jsoniter.Model._

/**
  * Created by Ilya Volynin on 13.09.2018 at 9:08.
  */
object ProducerSerMultiMessage extends KafkaProducerStreamWrapper {


  def createMultiMessage[K, V, PassThroughType](topic1: String, topic2: String,
                                                value1: SerializationBeanJsoniterBase, value2: SerializationBeanJsoniterBase,
                                                passThrough: PassThroughType): ProducerMessage.MultiMessage[String, SerializationBeanJsoniterBase, PassThroughType] = {
    import scala.collection.immutable
    // #multiMessage
    ProducerMessage.MultiMessage(
      immutable.Seq(
        new ProducerRecord(topic1, "", value1),
        new ProducerRecord(topic2, "", value2)
      ),
      passThrough
    )
    // #multiMessage
  }

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger, config: Config): Future[Any] = {
    import kafka.ser_on.jsoniter.KafkaSerJsoniter._
    val producerSettings =
      ProducerSettings(config, new StringSerializer, KafkaSerJsoniter.jsoniterScalaSerializer()).withBootstrapServers("localhost:9092")
    Source.fromIterator(() => Iterator.range(1500, 1505)).throttle(10, FiniteDuration(1, TimeUnit.SECONDS), 10, ThrottleMode.shaping).
      map(e => SerializationBeanJsoniter(e.toString, e, OffsetDateTime.now()))
      .map(value => createMultiMessage("testT1", "testT2", value, SerializationBeanJsoniter3(value.second), value.second))
      .via(Producer.flexiFlow(producerSettings))
      .map {
        case r@ProducerMessage.Result(metadata, message) =>
          val record = message.record
          s"single ${metadata.topic}/${metadata.partition} ${metadata.offset}: ${record.value}"
        case ProducerMessage.MultiResult(parts, passThrough) =>
          parts
            .map {
              case MultiResultPart(metadata, record) =>
                s"multi ${metadata.topic}/${metadata.partition} ${metadata.offset}: ${record.value}"
            }
            .mkString(", ")
        case ProducerMessage.PassThroughResult(passThrough) =>
          s"passed through"
      }
      .runWith(Sink.foreach(logger.warn))
  }
}
