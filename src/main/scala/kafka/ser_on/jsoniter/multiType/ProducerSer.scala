package kafka.ser_on.jsoniter.multiType
/**
  * Created by Ilya Volynin on 14.09.2018 at 14:23.
  */
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ThrottleMode}
import kafka.ser_on.jsoniter.JsoniterScalaSerialization
import org.apache.kafka.clients.producer.ProducerRecord
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
  def main(args: Array[String]): Unit = {
    // #plainSinkWithProducer
    val done = Source.fromIterator(() => Iterator.range(2200, 2210)).throttle(10, FiniteDuration(1, TimeUnit.SECONDS), 10, ThrottleMode.shaping).
      map {
        case e if e % 2 == 0 => SerializationBeanJsoniter(e.toString, e, OffsetDateTime.now())
        case e if e % 2 == 1 => SerializationBeanJsoniter2(e.toString, e, OffsetDateTime.now(), e)
      }
      .map(value => new ProducerRecord[String, SerializationBeanJsoniterBase]("testT2", value))
      .runWith(Producer.plainSink(producerSettings))
    //     plainSinkWithProducer
    Thread.sleep(3000)
    terminateWhenDone(done)
  }
}
