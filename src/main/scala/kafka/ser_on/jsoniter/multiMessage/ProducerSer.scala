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
import kafka.ser_on.jsoniter.JsoniterScalaSerialization
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.internals.Topic
import org.apache.kafka.common.serialization._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
/**
  * Created by Ilya Volynin on 13.09.2018 at 9:08.
  */
trait ProducerSer {
  val system = ActorSystem("example")
  // #producer
  // #settings
  val config = system.settings.config.getConfig("akka.kafka.producer")
  // #producer
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer.create(system)

  def terminateWhenDone(result: Future[Done]): Unit =
    result.onComplete {
      case Failure(e) =>
        system.log.error(e, e.getMessage)
        system.terminate()
      case Success(_) =>
        println("terminating...")
        system.terminate()
    }
}
object JupiterProducerExample extends ProducerSer {
  import kafka.ser_on.jsoniter.Model._
  val producerSettings =
    ProducerSettings(config, new StringSerializer, JsoniterScalaSerialization.jsoniterScalaSerializer()).withBootstrapServers("localhost:9092")

  //  val kafkaProducer = new KafkaProducer(
  //    Map[String, AnyRef](BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092").asJava,
  //    new StringSerializer,
  //    circeJsonSerializer[SerializationBean]
  //  )
  def createMultiMessage[K, V, PassThroughType](topic1: String, topic2: String,
                                                value1: SerializationBeanJsoniterBase, value2: SerializationBeanJsoniterBase,
                                                passThrough: PassThroughType) = {
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

  def main(args: Array[String]): Unit = {
    // #plainSinkWithProducer
    val done = Source.fromIterator(() => Iterator.range(1500, 10000)).throttle(10, FiniteDuration(1, TimeUnit.SECONDS), 10, ThrottleMode.shaping).
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
      .runWith(Sink.foreach(println(_)))
    // #plainSinkWithProducer
    Thread.sleep(3000)
    terminateWhenDone(done)
  }
}
