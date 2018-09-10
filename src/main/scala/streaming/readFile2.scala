package streaming
import java.io.File
import java.nio.file.Path

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink, Source}
import akka.util.ByteString
import streaming.BackPressure1.system
import scala.concurrent.duration._


import scala.concurrent.Await
/**
  * Created by Ilya Volynin on 16.06.2018 at 14:35.
  */
object readFile2 extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  // need to test
  // Given a stream of bytestrings delimited by the system line separator we can get lines represented as Strings
  val lines = Framing.delimiter(
    ByteString("\n"), maximumFrameLength = 1024).map(bs => bs.utf8String)
  // given as stream of Paths we read those files and count the number of lines
  val lineCounter = Flow[String].fold(0l)((count, line) => count + 1).toMat(Sink.head)(Keep.right)
  // Here's our test data source (replace paths with real paths)
  val testFiles = Source(List("tmp/ungrouped.csv", "tmp/example.csv").map(new File(_).toPath)).flatMapConcat(FileIO.fromPath(_)).via(lines)
  // Runs the line counter over the test files, returns a Future, which contains the number of lines, which we then print out to the console when it completes
  val resFuture = testFiles.runWith(lineCounter)
  val reply = Await.result(resFuture, 10 seconds)
  println(s"# lines in all files: $reply")
  Await.ready(system.terminate(), 10 seconds)

}
