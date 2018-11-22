/*package kafka.ser_on.circe

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.ovoenergy.kafka.serialization.circe._
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._

import scala.concurrent.Future
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
object PlainSinkWithProducerExample extends ProducerSer {
  import Model._

  val producerSettings =
    ProducerSettings(config, new StringSerializer, circeJsonSerializer[SerializationBean]).withBootstrapServers("localhost:9092")

//  val kafkaProducer = new KafkaProducer(
//    Map[String, AnyRef](BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092").asJava,
//    new StringSerializer,
//    circeJsonSerializer[SerializationBean]
//  )

  def main(args: Array[String]): Unit = {
    // #plainSinkWithProducer
    val done = Source(41 to 50).map(e => SerializationBean(e.toString, e, new java.util.Date()))
      .map(value => new ProducerRecord[String, SerializationBean]("testT1", value))
      .runWith(Producer.plainSink(producerSettings))
    // #plainSinkWithProducer
    Thread.sleep(3000)
    terminateWhenDone(done)
  }
}
  */
