package util
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by Ilya Volynin on 26.09.2018 at 15:40.
  */
trait StreamWrapperApp {

  def body()(implicit as: ActorSystem, mat: ActorMaterializer): Future[Any]

  def main(args: Array[String]): Unit = {
    implicit val as = ActorSystem()
    implicit val mat = ActorMaterializer()
    try Await.result(body, 60.minutes)
    finally as.terminate()
  }
}
