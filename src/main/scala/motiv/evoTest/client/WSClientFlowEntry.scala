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
import akka.http.scaladsl.model.ws._
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, writeToArray}
import motiv.evoTest.Model._
import util.StreamWrapperApp
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import motiv.evoTest.Util._

// sample run: "runMain motiv.evoTest.client.WSClientFlowEntry 21 40"

object WSClientFlowEntry extends StreamWrapperApp {

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    if (args.length != 2) return Future.failed(new Exception("please provide first and last indexes of range for adding table as program arguments. Thanks."))
//
    val incoming: Sink[Message, Future[Done]] =
    Sink.foreach {
      case message: TextMessage.Strict =>
        val out = readFromArray[Outgoing](message.text.getBytes("UTF-8"))
        println(out)
    }
    //
    val loginSource = Source.single(Login("admin", "admin"))
    val iter = Iterator.range(args(0).toInt, args(1).toInt)
    val newTables = Source.fromIterator(() => iter).map(i => AddTable(Table(i + 2, "table - Foo Fighters " + i * i, 4 * i), i + 1))
    val subscribeSource = Source.single(SubscribeTables)
    val aggSource = Source.combine[Incoming, Incoming](loginSource, subscribeSource, newTables)(Concat(_)).map(i => TextMessage(writeToArray[Incoming](i))
    )
    //
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9000/ws_api"))
    //
    //
    //
    //
    val (upgradeResponse, closed) =
    aggSource
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
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    // in a real application you would not side effect here
    connected.onComplete(println)
    closed.onComplete(_ => println("closed"))
    closed
    //no need to wait after
    //.flatMap(_ => akka.pattern.after(20000.millis, using = as.scheduler)(Future.successful()))
  }
}