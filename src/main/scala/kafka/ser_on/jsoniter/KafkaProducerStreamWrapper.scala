package kafka.ser_on.jsoniter
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.{Level, Logger}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Created by Ilya Volynin on 22.11.2018 at 10:53.
  */
trait KafkaProducerStreamWrapper {
  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger, config: Config): Future[Any]

  def main(args: Array[String]): Unit = {
    implicit val as: ActorSystem = ActorSystem()
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContext = as.dispatcher
    implicit lazy val root: Logger = LoggerFactory.getLogger(s"${this.getClass.getCanonicalName}").asInstanceOf[ch.qos.logback.classic.Logger]
    root.setLevel(Level.WARN)
    implicit val config: Config = ConfigFactory.load().getConfig("akka.kafka.producer")

    try Await.result(body(args), 60.minutes)
    finally as.terminate()
  }

}
