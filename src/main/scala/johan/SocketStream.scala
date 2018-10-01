/*
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package johan
import java.time.OffsetDateTime
import akka.NotUsed
import akka.pattern.Patterns.after
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, SourceShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import util.StreamWrapperApp
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.concurrent.duration._

/**
  * Accepts strings over websocket at ws://127.0.0.1/measurements
  * Protects the "database" by batching element in groups of 1000 but makes sure to at least
  * write every 1 second to not write too stale data or loose too much on failure.
  *
  * Based on this (great) blog article by Colin Breck:
  * http://blog.colinbreck.com/akka-streams-a-motivating-example/
  */
object SocketStream extends StreamWrapperApp {

  def body()(implicit as: ActorSystem, mat: ActorMaterializer): Future[Any] = {
    implicit val ec = as.dispatcher
    val measurementsFlow =
      Flow[Message].flatMapConcat { message =>
        // handles both strict and streamed ws messages by folding
        // the later into a single string (in memory)
        message.asTextMessage.asInstanceOf[TextMessage].textStream.fold("")(_ + _)
      }
        .groupedWithin(1000, 3.second)
        .mapAsync(5)(Database.asyncBulkInsert)
        .map(written => TextMessage(s"wrote up to: ${written.last}, ${OffsetDateTime.now()}"))
    val route =
      path("ws") {
        get {
          handleWebSocketMessages(measurementsFlow)
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
    after(15.seconds, as.scheduler, ec, Future())
  }
  object Database {

    def asyncBulkInsert(entries: Seq[String])(implicit as: ActorSystem): Future[Seq[String]] = {
      // dispatcher for returning future might be custom
      println(s"saved ${entries.size} messages, ${OffsetDateTime.now()}")
      after(30.millis, as.scheduler, as.dispatcher, Future(entries)(as.dispatcher))
    }
  }
}