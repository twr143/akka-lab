package motiv.evoTest
import java.nio.charset.StandardCharsets
import java.util.UUID
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.model.ws.{Message, TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import com.github.plokhotnyuk.jsoniter_scala.core._
import scala.concurrent.Future
import motiv.evoTest.Model._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.StdIn
import scala.util.control.NonFatal

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

  var adminLoggedInMap = mutable.Map[java.util.UUID, Boolean]()

  def flow(reqId: UUID): Flow[Message, Message, Any] = {
    Flow[Message]
      .collect {
        case tm: TextMessage ⇒ tm.textStream
      }
      .mapAsync(CORE_COUNT * 2 - 1)(in ⇒ in.runFold("")(_ + _)
        .map(in ⇒ readFromArray[Incoming](in.getBytes("UTF-8"))))
      .map {
        case login(login, password) if login == "admin" && password == "admin" && !adminLoggedInMap(reqId) ⇒
          adminLoggedInMap(reqId) = true
          login_successful(usertype = "admin", reqId)
        case login(login, _) ⇒ login_failed(login)
        case ping(seq) => pong(seq)
        case _: subscribe_tables => table_list(tables)
        case _: unsubscribe_tables => unsubscribed_from_tables
        case add_table(t, after_i) if adminLoggedInMap(reqId) =>
          tables = insert(tables, after_i, t)
          table_added(after_i, t)
        case update_table(t) if adminLoggedInMap(reqId) =>
          val i = findTableIndex(tables, t)
          if (i > -1) {
            tables = updateTableList(tables, t, i)
            table_updated(t)
          } else
            update_failed(t)
        case remove_table(id) if adminLoggedInMap(reqId) =>
          val i = findTableIndex(tables, id)
          if (i > -1) {
            tables = tables.filterNot(_.id == id)
            table_removed(id)
          } else
            remove_failed(id)
        case _: add_table | _: update_table | _: remove_table => not_authorized
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
}
