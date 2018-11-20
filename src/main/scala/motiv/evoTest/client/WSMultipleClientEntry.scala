package motiv.evoTest.client
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Concat, Flow, Keep, Sink, Source}
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, writeToArray}
import motiv.evoTest.Model.{AddTable, Incoming, Login, Outgoing, RemoveTable, SubscribeTables, Table, TableAdded, TableRemoved, UnsubscribeTables}
import util.StreamWrapperApp

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import motiv.evoTest.Util._

/**
  * Created by Ilya Volynin on 19.11.2018 at 15:20.
  */
object WSMultipleClientEntry extends StreamWrapperApp {

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    //
    def incoming: Sink[Message, Future[Done]] =
      Sink.foreach {
        case message: TextMessage.Strict =>
          val out = readFromArray[Outgoing](message.text.getBytes("UTF-8"))
          out match {
            case _: TableAdded | _:TableRemoved =>
            case theRest =>
              println(s" $theRest")
          }
      }

    //    val (start, end) = (args(0).toInt, args(1).toInt)
    val loginSource = Source.single(Login("admin", "admin"))
    //    val iterFirstHalf = Iterator.range(start, start + (end - start) / 2)
    //    val iterSecondHalf = Iterator.range(start + (end - start) / 2 + 1, end)
    //    val newTablesFirstHalf = Source.fromIterator(() => iterFirstHalf).map(i => AddTable(Table(i + 2, "table - Foo Fighters " + i * i, 4 * i), i + 1))
    //    val newTablesSecondHalf = Source.fromIterator(() => iterSecondHalf).map(i => AddTable(Table(i + 2, "table - Foo Fighters " + i * i, 4 * i), i + 1))
    val subscribeSource = Source.single(SubscribeTables)
    val unSubscribeSource = Source.single(UnsubscribeTables)
    val maybeSource = Source.maybe[Incoming]
    val tablesPerClient = 100

    def iterFirstHalf(startIdx: Int) = Iterator.range(startIdx * 100, startIdx * 100 + tablesPerClient)

    def newTablesSource(startIdx: Int) = Source.fromIterator(() => iterFirstHalf(startIdx)).map(i => AddTable(Table(i, "table - Foo Fighters " + i * i, 4 * i), i + 1))
    def removeTablesSource(startIdx: Int) =
      Source.fromIterator(() => iterFirstHalf(startIdx)).map(RemoveTable)

    def aggSource(index: Int) = Source.combine[Incoming, Incoming](loginSource,
      subscribeSource, //newTablesFirstHalf,
      //unSubscribeSource,
      newTablesSource(index),
      removeTablesSource(index)//newTablesSecondHalf
      //  ,maybeSource    //the first $breath will hang
    )(Concat(_)).map(incoming => TextMessage(writeToArray[Incoming](incoming))
    )

    def webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(
      WebSocketRequest("ws://localhost:9000/ws_api",
        scala.collection.immutable.Seq(Authorization(BasicHttpCredentials("ilya", "voly")))))

    val r = Source.fromIterator(() => Iterator.range(41, 56))
      .flatMapMerge(10, i => aggSource(i) .throttle(100, 200.millis)
      ).viaMat(webSocketFlow)(Keep.right)
      .alsoToMat(incoming)(Keep.both).runWith(Sink.ignore)
    r
  }

  def resultSink(implicit as: ActorSystem, ec: ExecutionContext): Sink[List[Future[Done]], Future[Done]] =
    Sink.foreach {
      list: List[Future[Done]] =>
        println(s"reached resultSink, size ${list.size}")
        Future.sequence(list)
    }
}
