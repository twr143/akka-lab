/*
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package johan
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import scala.util.{Failure, Success}
import scala.concurrent.duration._
/**
  * Natural numbers (up to maximum long, then it wraps around) as a service http://127.0.0.1/numbers
  */
object Sample3 extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val mat = ActorMaterializer()
  val numbers2 =
    Source.unfold(0L) { (n) =>
      val next = n + 1
      Thread.sleep(10)
      Some((next+10, next+2))
    }.map(n => ByteString(n + (if (n % 1000 == 0) "\n" else " "))).throttle(5, 1.second, 200, ThrottleMode.Shaping)
  //val numbers = Source.tick(1.second, 1.second, 0).map(n => n + 1).map(n => ByteString(n + (if (n % 1000 == 0) "\n" else " ")))
  val route =
    path("numbers") {
      get {
        complete(
          HttpResponse(entity = HttpEntity(`text/plain(UTF-8)`, numbers2))
        )
      }
    }
  val futureBinding = Http().bindAndHandle(route, "127.0.0.1", 8080)
  futureBinding.onComplete {
    case Success(binding) =>
      val address = binding.localAddress
      println(s"Akka HTTP server running at ${address.getHostString}:${address.getPort}")
    case Failure(ex) =>
      println(s"Failed to bind HTTP server: ${ex.getMessage}")
      ex.fillInStackTrace()
  }
}