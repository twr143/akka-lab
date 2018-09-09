package streaming.errorHandling
import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSystem
import akka.stream.{ActorAttributes, ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.ExecutionContext.Implicits.global
/**
  * Created by Ilya Volynin on 09.09.2018 at 14:08.
  */
object ErrorHandlingStream1 {
  def main(args: Array[String]): Unit ={
    implicit val system = ActorSystem("example")

    val graphDecider: Supervision.Decider = { e =>
      println(s"Caught ${ e } in graphDecider; stopping")
      Supervision.Stop
    }

    val matDecider: Supervision.Decider = { e =>
      println(s"Caught ${ e } in matDecider; stopping")
      Supervision.Stop
    }

    implicit val mat =
          ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(matDecider))
    val n = new AtomicLong(3)

    val stream = Source
      .single("hello")
      .map(_ => throw new Exception("first"))
      .recoverWithRetries(-1, {
        case e =>
          val current = n.decrementAndGet()

          if (current == 0) Source.single("hello")
          else Source.single("hello").map(_ => throw new Exception(s"ex ${ current }"))
      })
      .to(Sink.foreach(println))
      .withAttributes(ActorAttributes.supervisionStrategy(graphDecider))

    stream.run()
  }
}
