package util
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

/**
  * Created by Ilya Volynin on 26.09.2018 at 15:40.
  */
trait StreamWrapperApp {

  def body()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any]

  def main(args: Array[String]): Unit = {
    implicit val as: ActorSystem = ActorSystem()
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContext = as.dispatcher
    try Await.result(body, 60.minutes)
    finally as.terminate()
  }
}
