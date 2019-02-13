package motiv.evoTest.client
/**
  * Created by Ilya Volynin on 11.11.2018 at 20:24.
  */
import akka.actor.ActorSystem
import akka.Done
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.unmarshalling.Unmarshal
import ch.qos.logback.classic.Logger
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, writeToArray}
import motiv.evoTest.Model._
import util.StreamWrapperApp2

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import motiv.evoTest.Util._

import scala.util.Success

// sample run: "runMain motiv.evoTest.client.WSClientFlowEntry 21 40"
object WSClientFlowEntry extends StreamWrapperApp2 {

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger:Logger): Future[Any] = {
    if (args.length != 2) return Future.failed(new Exception("please provide first and last indexes of range for adding table as program arguments. Thanks."))
    //
    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach {
        case message: TextMessage.Strict =>
          val out = readFromArray[Outgoing](message.text.getBytes("UTF-8"))
          logger.warn(out.toString)
      }
    //
    val (start, end) = (args(0).toInt, args(1).toInt)
    val loginSource = Source.single(Login("admin", "admin"))
    val iterFirstHalf = Iterator.range(start, start + (end - start) / 2)
    val iterSecondHalf = Iterator.range(start + (end - start) / 2 + 1, end)
    val newTablesFirstHalf = Source.fromIterator(() => iterFirstHalf).map(i => AddTable(Table(i + 2, "table - Foo Fighters " + i * i, 4 * i), i + 1))
    val newTablesSecondHalf = Source.fromIterator(() => iterSecondHalf).map(i => AddTable(Table(i + 2, "table - Foo Fighters " + i * i, 4 * i), i + 1))
    val subscribeSource = Source.single(SubscribeTables)
    val unSubscribeSource = Source.single(UnsubscribeTables)
    val maybeSource = Source.maybe[Incoming]
    val aggSource = Source.combine[Incoming, Incoming](loginSource,
      subscribeSource, newTablesFirstHalf,
      unSubscribeSource, newTablesSecondHalf

      /*,maybeSource*/
      // if we wish infinite stream
    )(Concat(_)).map(i => TextMessage(writeToArray[Incoming](i))
    )
    //
    val webSocketFlow = Http().webSocketClientFlow(
      WebSocketRequest("ws://localhost:9000/ws_api",
        scala.collection.immutable.Seq(Authorization(BasicHttpCredentials("ilya", "voly")))))
    //
    //
    //
    //
    val (upgradeResponse, closed) =
    aggSource //.concatMat(Source.maybe[Incoming])(Keep.right)
      //I've added throttle to extend time of execution to allow this client receiving notifications
      //from other clients running in parallel
      // The client closes once the source has been completed
      .throttle(1, 1.second, 1, ThrottleMode.shaping)
      //
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .run()
    // just like a regular http request we can access response status which is available via upgrade.response.status
    // status code 101 (Switching Protocols) indicates that server support WebSockets
    val connected = upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Future.successful(Done)
      } else {
        val f = Unmarshal(upgrade.response.entity).to[String]
        f.onComplete {
          case Success(s) =>
            throw new RuntimeException(s"Connection failed: ${upgrade.response.status} $s")
        }
        f
      }
    }

    // in a real application you would not side effect here
    connected.onComplete(tr => logger.warn(tr.toString))
    closed.onComplete(_ => logger.warn("closed"))
    closed
    //no need to wait after
    //.flatMap(_ => akka.pattern.after(20000.millis, using = as.scheduler)(Future.successful()))
  }
}