package streaming.files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption._
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.util.ByteString
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future}
import util.DateTimeUtils._

/**
  * Created by Ilya Volynin on 12.03.2019 at 10:30.
  */
object GenerateMultiFiles extends StreamWrapperApp2 {

  val fileCount = 3

  def fullFileName(index: Long) = s"out/out${index % fileCount}.txt"

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val source = Source(1 to 10)
    val flow: Flow[Int, (String, Long), NotUsed] = Flow[Int].map(i => s"${i.toString}: $currentODT \n").zipWithIndex
    val fileFlow: Flow[(String, Long), IOResult, NotUsed] =
      Flow[(String, Long)].mapAsync(parallelism = 4) { pair ⇒
        Source.single(ByteString(pair._1)).runWith(FileIO.toPath(Paths.get(fullFileName(pair._2)), Set(WRITE, APPEND, CREATE)))
      }
    val fileSink: Sink[IOResult, Future[Done]] = Sink.foreach[IOResult] {
      println
    }
    source.via(flow).via(fileFlow).runWith(fileSink)
  }
}
