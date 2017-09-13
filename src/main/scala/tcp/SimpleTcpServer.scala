package tcp

import scala.util._
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import akka.util._

import scala.io.StdIn

object SimpleTcpServer extends App {

  val address = "127.0.0.1"
  val port = 6666

  val serverLogic = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n\r"), maximumFrameLength = 256, allowTruncation = true))
    .map(_.utf8String)
    .map(msg => s"Server hereby responds to message: $msg\n")
    .map(ByteString(_))

  val flow = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map(_.utf8String)
    .map(println)
    .map(_ ⇒ StdIn.readLine("> "))
    .map(_+"\n")
    .map(ByteString(_))


  def mkServer(address: String, port: Int)(implicit system: ActorSystem, materializer: Materializer): Unit = {
    import system.dispatcher

    val connectionHandler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      println(s"Incoming connection from: ${conn.remoteAddress}")
      conn.handleWith(flow)
    }
    val incomingCnnections = Tcp().bind(address, port)
    val binding = incomingCnnections.to(connectionHandler).run()

    binding onComplete {
      case Success(b) =>
        println(s"Server started, listening on: ${b.localAddress}")
      case Failure(e) =>
        println(s"Server could not be bound to $address:$port: ${e.getMessage}")
    }
  }

  def mkAkkaServer() = {
    implicit val server = ActorSystem("Server")
    implicit val materializer = ActorMaterializer()
    mkServer(address, port)
  }

  mkAkkaServer()
}