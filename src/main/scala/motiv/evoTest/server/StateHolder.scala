package motiv.evoTest.server
import java.util.UUID

import akka.actor.{Actor, ActorRef, Terminated}
import ch.qos.logback.classic.Logger
import motiv.evoTest.Model.{AddTable, Incoming, Login, LoginFailed, LoginSuccessful, NotAuthorized, Outgoing, Ping, Pong, RemoveFailed, RemoveTable, SubscribeTables, Table, TableAdded, TableList, TableRemoved, TableUpdated, UnsubscribeTables, UnsubscribedFromTables, UpdateFailed, UpdateTable}
import motiv.evoTest.server.JsoniterWSServerEntry._
import motiv.evoTest.server.RequestRouter.IncomingMessage
import motiv.evoTest.server.RouterManager.Notification

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * Created by Ilya Volynin on 13.02.2019 at 19:08.
  */
class StateHolder(routerManager: ActorRef)(implicit logger: Logger) extends Actor {
  import StateHolder._



  def receive: PartialFunction[Any, Unit] = {
    case IncomingMessage(o, reqId, routerActor) =>
      sender() ! Some(o).map(businessLogic(reqId, routerActor, routerManager)).get
  }
}

object StateHolder {

  val tables: mutable.ListBuffer[Table] = ListBuffer()

  val adminLoggedInMap = mutable.Map[java.util.UUID, Boolean]()

  def businessLogic(reqId: UUID, routerActor: ActorRef, routerManager: ActorRef)(implicit logger: Logger): PartialFunction[Incoming, Outgoing] = {
    case Login(login, password) if login == "admin" && password == "admin" ⇒
      adminLoggedInMap += (reqId -> true)
      //      logger.warn("adminLoggedInMap at login = {}, reqId = {}", adminLoggedInMap, reqId.toString, new Object)
      LoginSuccessful(usertype = "admin", reqId)
    case Login(login, password) if login == "user" && password == "user" ⇒
      LoginSuccessful(usertype = "user", reqId)
    case Login(login, _) ⇒ LoginFailed(login)
    case Ping(seq) => Pong(seq)
    case SubscribeTables =>
      subscribers += routerActor
      TableList(tables.map(_.id).take(500), tables.size)
    case UnsubscribeTables =>
      subscribers -= routerActor
      UnsubscribedFromTables
    case AddTable(t, after_i) => if (adminLoggedInMap(reqId)) {
      insert(tables, after_i, t)
      val added = TableAdded(after_i, t, reqId)
      sendNotification(routerManager, routerActor, added)
      added
    }
    else
      NotAuthorized("AddTable", reqId)
    case UpdateTable(t) if adminLoggedInMap(reqId) =>
      val i = findTableIndex(tables, t)
      if (i > -1) {
        updateTableList(tables, t, i)
        val updated = TableUpdated(t)
        sendNotification(routerManager, routerActor, updated)
        updated
      } else
        UpdateFailed(t)
    case RemoveTable(id) if adminLoggedInMap(reqId) =>
             val i = findTableIndex(tables, id)
              if (i > -1) {
                tables.remove(i)
                val removed = TableRemoved(id)
                sendNotification(routerManager, routerActor, removed)
                removed
              } else
                RemoveFailed(id)
    case _: UpdateTable => NotAuthorized("UpdateTable", reqId)
    case _: RemoveTable => NotAuthorized("RemoveTable", reqId)
  }

  def insert(list: ListBuffer[Table], after_i: Int, value: Table) = {
    //      val index = list.indexWhere(_.id == after_i)
    //      list.take(index + 1) ++ List(value) ++ list.drop(index + 1)
    list.append(value)
  }

  def findTableIndex(list: ListBuffer[Table], value: Table): Int = {
    list.indexWhere(_.id == value.id)
  }

  def findTableIndex(list: ListBuffer[Table], id: Int): Int = {
    list.indexWhere(_.id == id
      //      (if (id > list.size - 1) id - 1 else id)
    )
  }

  def updateTableList(list: ListBuffer[Table], value: Table, index: Int): ListBuffer[Table] = {
    list.take(index) ++ ListBuffer(value) ++ list.drop(index + 1)
  }

  def sendNotification(routerManager: ActorRef, routerActor: ActorRef, message: Outgoing): Unit = {
    val common = subscribers.intersect(subscribersToRemove)
    subscribers = subscribers -- common
    subscribersToRemove = subscribersToRemove -- common
    routerManager ! Notification(subscribers - routerActor, message)
  }
}

