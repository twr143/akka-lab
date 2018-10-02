package motiv.jsoniterWsServer
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import com.github.plokhotnyuk.jsoniter_scala.core._

import scala.concurrent.Future
import motiv.jsoniterWsServer.Model._

import scala.io.StdIn
import scala.util.control.NonFatal

/**
  * Created by Ilya Volynin on 02.10.2018 at 16:51.
  */
/**
  * usage:
  * send
  * {
  * "login":"admin",
  * "password":"admin"
  * }
  * to receive
  * {"type":"WelcomeResponse","login":"admin","message":"successfully logged in"}
  *
  * send
  * {
  * "login":"admin1",
  * "password":"admin"
  * }
  * to receive
  * {"type":"DenyResponse","login":"admin1","message":"bad credentials"}
  */
object JsoniterWSServerEntry extends App {

  val CORE_COUNT = 2

  implicit val system = ActorSystem("example")

  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  implicit def convertArrayOfBytesToString(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)

  def flow: Flow[Message, Message, Any] = {
    Flow[Message]
      .collect {
        case tm: TextMessage ⇒ tm.textStream
      }
      .mapAsync(CORE_COUNT * 2 - 1)(in ⇒ in.runFold("")(_ + _)
        .map(in ⇒ readFromArray[Creds](in.getBytes("UTF-8"))))
      .map {
        case Creds(login, password) if login == "admin" && password == "admin" ⇒
          WelcomeResponse(login)
        case Creds(login, _) ⇒ DenyResponse(login)
      }
      .mapAsync(CORE_COUNT * 2 - 1)(out ⇒ Future(TextMessage(writeToArray[Outgoing](out))))
      .recover {
        case e: JsonParseException => TextMessage(writeToArray[Outgoing](InvalidBody(e.getMessage)))
        case NonFatal(e) => TextMessage(writeToArray[Outgoing](GeneralException(e.getMessage)))
      }
  }

  val route = path("ws_api")(handleWebSocketMessages(flow))

  val bindingFuture = Http().bindAndHandle(route, "localhost", 9000)

  println(s"Server online at http://localhost:9000/\nPress RETURN to stop...")

  StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ ⇒ {
      system.terminate()
    })
}
