package motiv.evoTest.server
import java.nio.charset.StandardCharsets
import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorAttributes, ActorMaterializer, OverflowStrategy, Supervision}
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonParseException, readFromArray, writeToArray}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn
import scala.util.Random
import scala.util.control.NonFatal
import motiv.evoTest.Model._
import motiv.evoTest.server.RequestRouter._
import motiv.evoTest.server.RouterManager._
import akka.stream.contrib.Implicits.TimedFlowDsl
import scala.concurrent.duration._

/**
  * Created by Ilya Volynin on 02.10.2018 at 16:51.
  *
  * launch: sbt "runMain jsoniterWsServer.JsoniterWSServerEntry"
  */
object JsoniterWSServerEntry extends App {

  val CORE_COUNT = 2

  implicit val system = ActorSystem("example")

  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  var tables: List[Table] = List(Table(1, "table - James Bond", 7), Table(2, "table - Mission Impossible", 4))

  var adminLoggedInMap = Map[java.util.UUID, Boolean]()

  var subscribers = Set[ActorRef]()

  val countNum = 1000

  def timeCheck(duration: FiniteDuration): Unit = {
    println(s"$countNum elements passed in ${duration.toMillis}")
  }

  val routerManager = system.actorOf(Props[RouterManager], "routerManager")

  def decider (router: ActorRef): Supervision.Decider = {
    case e: JsonParseException ⇒
      router ! IncomingMessage(InvalidBody(e.getMessage))
      Supervision.Resume
    case NonFatal(e) ⇒
      router ! IncomingMessage(GeneralException(e.getMessage))
      Supervision.Stop
  }

  def flow(reqId: UUID): Flow[Message, Message, Any] = {
    val routerActor = system.actorOf(Props(new RequestRouter(routerManager)))
    val incoming = Flow[Message]
      .collect {
        case tm: TextMessage ⇒ tm.textStream
      }
      .mapAsync(CORE_COUNT * 2 - 1)(in ⇒ in.runFold("")(_ + _)
        .map(in ⇒ readFromArray[Incoming](in.getBytes("UTF-8"))))
      .scan(0, Ping(0): Incoming)((t, out) => (t._1 + 1, out))
      .timedIntervalBetween(_._1 % countNum == 0, timeCheck).map(_._2)
      .map {
        case Login(login, password) if login == "admin" && password == "admin" && !adminLoggedInMap(reqId) ⇒
          adminLoggedInMap = adminLoggedInMap.updated(reqId, true)
          LoginSuccessful(usertype = "admin", reqId)
        case Login(login, password) if login == "user" && password == "user" ⇒
          LoginSuccessful(usertype = "user", reqId)
        case Login(login, _) ⇒ LoginFailed(login)
        case Ping(seq) => Pong(seq)
        case SubscribeTables =>
          subscribers += routerActor
          TableList(tables.take(100))
        case UnsubscribeTables =>
          subscribers -= routerActor
          UnsubscribedFromTables
        case AddTable(t, after_i) if adminLoggedInMap(reqId) =>
          tables = insert(tables, after_i, t)
          val added = TableAdded(after_i, t)
          routerManager ! Notification(routerActor, subscribers, added)
          added
        case UpdateTable(t) if adminLoggedInMap(reqId) =>
          val i = findTableIndex(tables, t)
          if (i > -1) {
            tables = updateTableList(tables, t, i)
            val updated = TableUpdated(t)
            routerManager ! Notification(routerActor, subscribers, updated)
            updated
          } else
            UpdateFailed(t)
        case RemoveTable(id) if adminLoggedInMap(reqId) =>
          val i = findTableIndex(tables, id)
          if (i > -1) {
            tables = tables.filterNot(_.id == id)
            val removed = TableRemoved(id)
            routerManager ! Notification(routerActor, subscribers, removed)
            removed
          } else
            RemoveFailed(id)
        case _: AddTable | _: UpdateTable | _: RemoveTable => NotAuthorized
      }
      .map(IncomingMessage)
      .to(Sink.actorRef[IncomingMessage](routerActor, PoisonPill))
      .withAttributes(ActorAttributes.supervisionStrategy(decider(routerActor)))
    val outgoing: Source[Message, NotUsed] =
      Source.actorRef[OutgoingMessage](1000, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
          // give the user actor a way to send messages out
          routerActor ! Connected(outActor)
          NotUsed
        }.keepAlive(10.seconds, () => OutgoingMessage(Pong(Random.nextInt(100))))
        .mapAsync(CORE_COUNT * 2 - 1)(outgoing ⇒ Future(TextMessage(writeToArray[Outgoing](outgoing.obj))))
    Flow.fromSinkAndSource(incoming, outgoing)
  }

  val route: HttpRequest => HttpResponse = {
    case req@HttpRequest(HttpMethods.GET, Uri.Path("/ws_api"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) =>
          val reqId = UUID.randomUUID()
          adminLoggedInMap += (reqId -> false)
          upgrade.handleMessages(flow(reqId))
        case None => HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  val bindingFuture = Http().bindAndHandleSync(route, "localhost", 9000)

  println(s"Server online at http://localhost:9000/\nPress RETURN to stop...")

  StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ ⇒ {
      system.terminate()
    })

  def insert(list: List[Table], after_i: Int, value: Table) = {
    val index = list.indexWhere(_.id == after_i)
    list.take(index + 1) ++ List(value) ++ list.drop(index + 1)
  }

  def findTableIndex(list: List[Table], value: Table): Int = {
    list.indexWhere(_.id == value.id)
  }

  def findTableIndex(list: List[Table], id: Int): Int = {
    list.indexWhere(_.id == id)
  }

  def updateTableList(list: List[Table], value: Table, index: Int): List[Table] = {
    list.take(index) ++ List(value) ++ list.drop(index + 1)
  }
}
