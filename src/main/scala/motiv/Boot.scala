package motiv
import akka.actor.{Actor, Props}
import akka.stream.scaladsl.Flow
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.server.Directives._
import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import io.circe.parser._
import kamon.Kamon
import kamon.prometheus.PrometheusReporter

import scala.concurrent.Future
import scala.io.StdIn
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._

/*
created by Ilya Volynin at 13.09.17

example message:
{
 "name":"i12hlya",
 "age":13
}

*/
object Boot extends App {
  implicit val system = ActorSystem("example")
  implicit val materializer = ActorMaterializer()
  trait WsIncome
  trait WsOutgoing
  case object Tick extends WsOutgoing
  @JsonCodec case class Say(name: String, age: Int) extends WsIncome with WsOutgoing
  Kamon.addReporter(new PrometheusReporter())
  implicit val WsIncomeDecoder: Decoder[WsIncome] = Decoder[Say] {
    Decoder.forProduct2("name", "age")(Say.apply)
  }.map[WsIncome](identity)
  implicit val enc: Encoder[Say] = Encoder.forProduct2("name", "age")(s =>
    (s.name, s.age))
  implicit val WsOutgoingEncoder: Encoder[WsOutgoing] = Encoder {
    case s: Say ⇒ s.asJson
    case Tick ⇒ Json.obj("time" → DateTime.now.toIsoDateTimeString().asJson)
  }
  val CORE_COUNT = 2

  val broadcast = Source.tick[WsOutgoing](1.second, 1.second, Tick)
  def flow: Flow[Message, Message, Any] = {
    Flow[Message]
      .collect {
        case tm: TextMessage ⇒ tm.textStream
      }
      .mapAsync(CORE_COUNT * 2 - 1)(in ⇒ in.runFold("")(_ + _).flatMap(in ⇒ Future.fromTry(parse(in).toTry.flatMap(_.as[WsIncome].toTry))))

      .collect {
        case Say(name, age) ⇒
          println(s"ok $name, $age")
          Say(s"hello: $name", age + 5)
      }
//      .merge(broadcast) // don't want to include
      .mapAsync(CORE_COUNT * 2 - 1)(out ⇒ Future(TextMessage(out.asJson.noSpaces)))
  }

  val route = path("ws")(handleWebSocketMessages(flow))
  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")

  StdIn.readLine()
  import system.dispatcher
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ ⇒ {
      Kamon.stopAllReporters()
      system.terminate()
    })
  //start 7
  //b81
  //ori7
}

