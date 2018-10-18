package motiv.evoTest
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.model.ws.{Message, TextMessage, UpgradeToWebSocket}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import com.github.plokhotnyuk.jsoniter_scala.core._
import scala.concurrent.Future
import motiv.evoTest.Model._
import scala.collection.mutable.ListBuffer
import scala.io.StdIn
import scala.util.control.NonFatal
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

  implicit def convertArrayOfBytesToString(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)

  var tables: List[Table] = List(Table(1, "table - James Bond", 7), Table(2, "table - Mission Impossible", 4))

  var adminLoggedInMap = Map[java.util.UUID, Boolean]()

  var subscribedEvents = Map[java.util.UUID, ListBuffer[String]]()

  val countNum = 1000

  def timeCheck(duration: FiniteDuration): Unit = {
    println(s"$countNum elements passed in ${duration.toMillis}")
  }

  def flow(reqId: UUID): Flow[Message, Message, Any] = {
    Flow[Message]
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
          if (!subscribedEvents.contains(reqId))
            subscribedEvents += (reqId -> ListBuffer.empty)
          TableList(tables.take(100))
        case UnsubscribeTables =>
          subscribedEvents -= reqId
          UnsubscribedFromTables
        case AddTable(t, after_i) if adminLoggedInMap(reqId) =>
          tables = insert(tables, after_i, t)
          updateSubscribed(s"added ${t.id}")
          TableAdded(after_i, t)
        case UpdateTable(t) if adminLoggedInMap(reqId) =>
          val i = findTableIndex(tables, t)
          if (i > -1) {
            tables = updateTableList(tables, t, i)
            updateSubscribed(s"updated ${t.id}")
            TableUpdated(t)
          } else
            UpdateFailed(t)
        case RemoveTable(id) if adminLoggedInMap(reqId) =>
          val i = findTableIndex(tables, id)
          if (i > -1) {
            tables = tables.filterNot(_.id == id)
            updateSubscribed(s"removed $id")
            TableRemoved(id)
          } else
            RemoveFailed(id)
        case _: AddTable | _: UpdateTable | _: RemoveTable => NotAuthorized
        case QueryChanges => // assume periodic polling from client
          if (subscribedEvents.contains(reqId)) {
            val events = subscribedEvents(reqId).clone()
            subscribedEvents(reqId).clear()
            Changes(events.take(100))
          }
          else NotSubscribed
      }
      .mapAsync(CORE_COUNT * 2 - 1)(out ⇒ Future(TextMessage(writeToArray[Outgoing](out))))
      .recover {
        case e: JsonParseException => TextMessage(writeToArray[Outgoing](InvalidBody(e.getMessage)))
        case NonFatal(e) => TextMessage(writeToArray[Outgoing](GeneralException(e.getMessage)))
      }
  }

  //  val route = path("ws_api")(handleWebSocketMessages(flow))
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

  def updateSubscribed(newStatus: String): Unit = {
    subscribedEvents = subscribedEvents.transform((_, vals) => vals += newStatus)
  }
}
