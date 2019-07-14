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
import ch.qos.logback.classic.Logger
import util.{DateTimeUtils, StreamWrapperApp2}
import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import scala.io.StdIn

/**
  * Accepts strings over websocket at ws://127.0.0.1/measurements
  * Protects the "database" by batching element in groups of 1000 but makes sure to at least
  * write every 1 second to not write too stale data or loose too much on failure.
  *
  * Based on this (great) blog article by Colin Breck:
  * http://blog.colinbreck.com/akka-streams-a-motivating-example/
  */
object GroupedWithinSocketStream extends StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val measurementsFlow =
      Flow[Message].flatMapConcat { message =>
        // handles both strict and streamed ws messages by folding
        // the later into a single string (in memory)
        message.asTextMessage.asInstanceOf[TextMessage].textStream.fold("")(_ + _)
      }
        .groupedWithin(1000, 3.second)
        .mapAsync(5)(Database.asyncBulkInsert)
        .map(written => TextMessage(s"wrote up to: ${written.last}, ${DateTimeUtils.currentODT}"))
    val route =
      path("ws") {
        get {
          handleWebSocketMessages(measurementsFlow)
        }
      }
    val futureBinding = Http().bindAndHandle(route, "127.0.0.1", 8080).andThen {
      case Success(binding) =>
        val address = binding.localAddress
        logger.warn(s"Akka HTTP server running at ${address.getHostString}:${address.getPort}")
      case Failure(ex) =>
        logger.error("Failed to bind HTTP server: {}", ex.getMessage)
        ex.fillInStackTrace()
    }
    logger.warn(f"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()
    futureBinding.flatMap {
      _.unbind()
    }
  }

  object Database {

    def asyncBulkInsert(entries: Seq[String])(implicit as: ActorSystem, logger: Logger): Future[Seq[String]] = {
      // dispatcher for returning future might be custom
      logger.warn(s"saved {} messages, {}", entries.size, DateTimeUtils.currentODT)
      after(30.millis, as.scheduler, as.dispatcher, Future(entries)(as.dispatcher))
    }
  }

}