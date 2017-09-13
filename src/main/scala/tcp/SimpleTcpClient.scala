package tcp
import scala.io.StdIn
import scala.util._

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._
import akka.util._
/*
created by Ilya Volynin at 13.09.17
*/
object SimpleTcpClient extends App {

  implicit val client = ActorSystem("SimpleTcpClient")
  implicit val materializer = ActorMaterializer()

  val address = "127.0.0.1"
  val port = 6666

  val connection = Tcp().outgoingConnection(address, port)

  val flow = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map(_.utf8String)
    .map(println)
    .map(_ ⇒ StdIn.readLine("> "))
    .map(_+"\n")
    .map(ByteString(_))

  connection.join(flow).run()
}
