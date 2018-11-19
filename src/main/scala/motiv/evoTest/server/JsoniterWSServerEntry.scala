package motiv.evoTest.server
import java.nio.charset.StandardCharsets
import java.util.UUID
import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream._
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonParseException, readFromArray, writeToArray}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn
import scala.util.{Failure, Random, Success}
import scala.util.control.NonFatal
import motiv.evoTest.Model._
import motiv.evoTest.Util._
import motiv.evoTest.server.RequestRouter._
import motiv.evoTest.server.RouterManager._
import akka.stream.contrib.Implicits.TimedFlowDsl
import motiv.evoTest.server.JsoniterWSServerEntry.subscribers
import util.StreamWrapperApp
import scala.collection.immutable
import scala.concurrent.duration._

/**
  * Created by Ilya Volynin on 02.10.2018 at 16:51.
  *
  * launch: sbt "runMain jsoniterWsServer.JsoniterWSServerEntry"
  */
object JsoniterWSServerEntry extends StreamWrapperApp {

  val CORE_COUNT = 2

  var tables: List[Table] = List(Table(1, "table - James Bond", 7), Table(2, "table - Mission Impossible", 4))

  var adminLoggedInMap = Map[java.util.UUID, Boolean]()

  var subscribers = Set[ActorRef]()

  var subscribersToRemove = Set[ActorRef]()

//  var sharedKilSwitches = Set[SharedKillSwitch]()

  val countNum = 1000

  def timeCheck(duration: FiniteDuration): Unit = {
    println(s"$countNum elements passed in ${duration.toMillis}")
  }

  def decider(router: ActorRef): Supervision.Decider = {
    case e: JsonParseException ⇒
      router ! IncomingMessage(InvalidBody(e.getMessage))
      Supervision.Resume
    case NonFatal(e) ⇒
      router ! IncomingMessage(GeneralException(e.getMessage + e.printStackTrace()))
      Supervision.Stop
  }

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    val routerManager = as.actorOf(Props[RouterManager], "routerManager")
    val sharedKS = KillSwitches.shared(s"kill-switch")

    def flow(reqId: UUID): Flow[Message, Message, Any] = {
      val routerActor = as.actorOf(Props(new RequestRouter(routerManager)), name = s"route-$reqId")
      val incoming = Flow[Message]
        .watchTermination()((_, futDone: Future[Done]) =>
          futDone.onComplete {
            case Success(_) ⇒
              subscribersToRemove += routerActor
            case Failure(t) ⇒ println(s"router flow failed for req: $reqId")
          })
        .collect {
          case tm: TextMessage ⇒ tm.textStream
        }
        .mapAsync(CORE_COUNT * 2 - 1)(in ⇒ in.runFold("")(_ + _)
          .map(in ⇒ readFromArray[Incoming](in.getBytes("UTF-8"))))
        //        .scan(Ping(0): Incoming, 0)((t, out) => (out, t._2 + 1)) //1
        .zipWith(Source.fromIterator(() => Iterator.from(0))) {
        //2  1 equiv. 2    2 is faster
        (incoming, counter) => (incoming, counter)
      }
        //        .zipWithIndex    //3   1 = 2 = 3      2 is the fastest
        .timedIntervalBetween(_._2 % countNum == 0, timeCheck).map(_._1)
        .map(businessLogic(reqId, routerActor, routerManager))
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
          .mapAsync(CORE_COUNT * 2 - 1)(o => Future.successful(o.obj).map(writeToArray[Outgoing](_)).map(TextMessage(_)))
      Flow.fromSinkAndSource(incoming, outgoing).via(sharedKS.flow)
    }

    val route: HttpRequest => HttpResponse = {
      case req@HttpRequest(HttpMethods.GET, Uri.Path("/ws_api"), headers: immutable.Seq[HttpHeader], _, _) =>
        req.header[UpgradeToWebSocket] match {
          case Some(upgrade) =>
            val reqId = UUID.randomUUID()
            adminLoggedInMap += (reqId -> false)
            upgrade.handleMessages(flow(reqId))
          //            req.header[Authorization] match {
          //              case Some(authorization) =>
          //                authorization.credentials match {
          //                  case BasicHttpCredentials(u, p) if "ilya"==u && "voly" == p =>
          //                    val reqId = UUID.randomUUID()
          //                    adminLoggedInMap += (reqId -> false)
          //                    upgrade.handleMessages(flow(reqId))
          //                  case BasicHttpCredentials(u, p) =>
          //                    HttpResponse(403, entity = "Wrong Credentials!")
          //                  case _ =>
          //                    HttpResponse(403, entity = "Please provide basic credentials!")
          //                }
          //              case None => HttpResponse(403, entity = "Authorization header required!")
          //            }
          case None => HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        HttpResponse(404, entity = "Unknown resource!")
    }
    val bindingFuture = Http().bindAndHandleSync(route, "localhost", 9000)
    println(s"Server online at http://localhost:9000/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture.flatMap {
      routerManager ! PoisonPill
      sharedKS.shutdown
      _.unbind()
    }
  }

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

  def sendNotification(routerManager: ActorRef, routerActor: ActorRef, message: Outgoing): Unit = {
    val common = subscribers.intersect(subscribersToRemove)
    subscribers = subscribers -- common
    subscribersToRemove = subscribersToRemove -- common
    routerManager ! Notification(subscribers - routerActor, message)
  }

  def businessLogic(reqId: UUID, routerActor: ActorRef, routerManager: ActorRef): PartialFunction[Incoming, Outgoing] = {
    case Login(login, password) if login == "admin" && password == "admin" &&
      ((adminLoggedInMap.contains(reqId) && !adminLoggedInMap(reqId)) || !adminLoggedInMap.contains(reqId)) ⇒
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
      sendNotification(routerManager, routerActor, added)
      added
    case UpdateTable(t) if adminLoggedInMap(reqId) =>
      val i = findTableIndex(tables, t)
      if (i > -1) {
        tables = updateTableList(tables, t, i)
        val updated = TableUpdated(t)
        sendNotification(routerManager, routerActor, updated)
        updated
      } else
        UpdateFailed(t)
    case RemoveTable(id) if adminLoggedInMap(reqId) =>
      val i = findTableIndex(tables, id)
      if (i > -1) {
        tables = tables.filterNot(_.id == id)
        val removed = TableRemoved(id)
        sendNotification(routerManager, routerActor, removed)
        removed
      } else
        RemoveFailed(id)
    case _: AddTable | _: UpdateTable | _: RemoveTable => NotAuthorized
  }
}
