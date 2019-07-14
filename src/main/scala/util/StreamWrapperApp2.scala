package util
import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by Ilya Volynin on 20.11.2018 at 16:56.
  */
trait StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any]

  def main(args: Array[String]): Unit = {
    implicit val as: ActorSystem = ActorSystem()
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContext = as.dispatcher
    implicit lazy val root: Logger = LoggerFactory.getLogger(s"${this.getClass.getCanonicalName}".replace("$", "")).asInstanceOf[ch.qos.logback.classic.Logger]
    root.setLevel(Level.WARN)
    try Await.result(body(args), 60.minutes)
    finally as.terminate()
  }
}

object StreamWrapperApp2 {

  def timeCheckWrapper(f: () => Future[Done], name: String)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Done] = {
    val start = System.currentTimeMillis()
    f().andThen {
      case Success(x) =>
        logger.warn("{} completed for: {} ms", name.padTo(35,' '), (System.currentTimeMillis() - start).toString.padTo(5,' '), new Object)
      case Failure(e) =>
        logger.error("Failure: {}", e.getMessage)
        as.terminate()
    }
  }
}
