package motiv.evoTest.client
/**
  * Created by Ilya Volynin on 11.11.2018 at 20:24.
  */
import akka.actor.ActorSystem
import akka.Done
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, writeToArray}
import motiv.evoTest.Model._
import util.StreamWrapperApp
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object WSClientFlowEntry extends StreamWrapperApp {

  override def body()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    // Future[Done] is the materialized value of Sink.foreach,
    // emitted when the stream completes
    val incoming: Sink[Message, Future[Done]] =
    Sink.foreach {
      case message: TextMessage.Strict =>
        val out = readFromArray[Outgoing](message.text.getBytes("UTF-8"))
        println(out)
    }
    // send this as a message over the WebSocket
    val loginSource = Source.single(Login("admin", "admin"))
    val iter = Iterator.range(0, 10)
    val newTables = Source.fromIterator(() => iter).map(i => AddTable(Table(i + 2, "table - Foo Fighters " + i * i, 4 * i), i + 1))
    val subscribeSource = Source.single(SubscribeTables)
    val aggSource = Source.combine[Incoming, Incoming](loginSource, newTables, subscribeSource)(Concat(_)).map(i => TextMessage(writeToArray[Incoming](i))
    )
    // flow to use (note: not re-usable!)
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9000/ws_api"))
    // the materialized value is a tuple with
    // upgradeResponse is a Future[WebSocketUpgradeResponse] that
    // completes or fails when the connection succeeds or fails
    // and closed is a Future[Done] with the stream completion from the incoming sink
    val (upgradeResponse, closed) =
    aggSource
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .run()
    // just like a regular http request we can access response status which is available via upgrade.response.status
    // status code 101 (Switching Protocols) indicates that server support WebSockets
    val connected = upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Future.successful(Done)
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    // in a real application you would not side effect here
    connected.onComplete(println)
    closed.onComplete(_ => println("closed"))
    closed//.flatMap(_ => akka.pattern.after(1000.millis, using = as.scheduler)(Future.successful()))
  }
}