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
import motiv.evoTest.Model.{AddTable, Incoming, Login, Outgoing, SubscribeTables, Table, UnsubscribeTables}
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
    def incoming(idx: Int): Sink[Message, Future[Done]] =
      Sink.foreach {
        case message: TextMessage.Strict =>
          val out = readFromArray[Outgoing](message.text.getBytes("UTF-8"))
          println(s"$idx: $out")
      }

    //    val (start, end) = (args(0).toInt, args(1).toInt)
    val loginSource = Source.single(Login("admin", "admin"))
    //    val iterFirstHalf = Iterator.range(start, start + (end - start) / 2)
    //    val iterSecondHalf = Iterator.range(start + (end - start) / 2 + 1, end)
    //    val newTablesFirstHalf = Source.fromIterator(() => iterFirstHalf).map(i => AddTable(Table(i + 2, "table - Foo Fighters " + i * i, 4 * i), i + 1))
    //    val newTablesSecondHalf = Source.fromIterator(() => iterSecondHalf).map(i => AddTable(Table(i + 2, "table - Foo Fighters " + i * i, 4 * i), i + 1))
    val subscribeSource =  Source.single(SubscribeTables)
    val unSubscribeSource = Source.single(UnsubscribeTables)
    val maybeSource =  Source.maybe[Incoming]
    val aggSource = Source.combine[Incoming, Incoming](loginSource,
      subscribeSource, //newTablesFirstHalf,
      unSubscribeSource //newTablesSecondHalf
      //,maybeSource    //the first $breath will hang
      // if we wish infinite stream
    )(Concat(_)).map(i => TextMessage(writeToArray[Incoming](i))
    )

    def webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(
      WebSocketRequest("ws://localhost:9000/ws_api",
        scala.collection.immutable.Seq(Authorization(BasicHttpCredentials("ilya", "voly")))))

    val r = Source.fromIterator(() => Iterator.range(1, 111))
      .flatMapMerge(10, i => aggSource.throttle(1, 200.millis)
        .viaMat(webSocketFlow)(Keep.right)
        .alsoToMat(incoming(i))(Keep.both)
      ).runWith(Sink.ignore)
    r
  }

  def resultSink(implicit as: ActorSystem, ec: ExecutionContext): Sink[List[Future[Done]], Future[Done]] =
    Sink.foreach {
      list: List[Future[Done]] =>
        println(s"reached resultSink, size ${list.size}")
        Future.sequence(list)
    }

}
