package kafka.ser_on.jsoniter
import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.pattern.Patterns.after
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.{Level, Logger}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by Ilya Volynin on 21.11.2018 at 15:49.
  */
trait KafkaConsumerStreamWrapper {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger, config: Config): DrainingControl[Done]

  def main(args: Array[String]): Unit = {
    if (args.length != 1) throw new Exception("please provide the topic name to read as the program argument. Thanks.")

    implicit val as: ActorSystem = ActorSystem()
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContext = as.dispatcher
    implicit lazy val root: Logger = LoggerFactory.getLogger(s"${this.getClass.getCanonicalName}").asInstanceOf[ch.qos.logback.classic.Logger]
    root.setLevel(Level.WARN)
    implicit val config: Config = ConfigFactory.load().getConfig("akka.kafka.consumer")
    val control = body(args)
    as.scheduler.scheduleOnce(3000.millis)({
      control.drainAndShutdown().onComplete {
            case Failure(e) =>
              root.error(e.getMessage, e)
              as.terminate()
            case Success(_) => as.terminate()
          }
    })

  }
}
