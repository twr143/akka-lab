/*
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package johan
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, OverflowStrategy, ThrottleMode}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success}
import scala.concurrent.duration._

/**
  * Natural numbers as a service http://127.0.0.1/numbers
  */
object GetUrlStream extends StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    implicit val ec: ExecutionContextExecutor = as.dispatcher
    val promise = Promise[Unit]()
    val numbers2 =
      Source.unfold(0L) { n =>
        Some(n + 11, n + 3)
      }.take(250).map(n => ByteString((if (n % 1000 < 11 && n > 11) "\n" else if (n > 11) " " else "") + n))
        .buffer(1000, overflowStrategy = OverflowStrategy.backpressure)
        .throttle(1, 100.millis, 200, ThrottleMode.Shaping)
        .watchTermination()((_, futDone: Future[Done]) =>
          futDone.onComplete {
            case Success(_) ⇒
              promise.complete(Success(()))
            case Failure(t) ⇒ logger.error(s"numbers2 stream failed: {}", t.getMessage)
          })
    val route =
      path("numbers") {
        get {
          complete(
            HttpResponse(entity = HttpEntity(`text/plain(UTF-8)`, numbers2))
          )
        }
      }
    Http().bindAndHandle(route, "localhost", 8080)
    .andThen {
      case Success(binding) =>
        val address = binding.localAddress
        logger.warn("Akka HTTP server running at {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        logger.error("Failed to bind HTTP server: {}", ex.getMessage)
        ex.fillInStackTrace()
    }
    promise.future
  }
}