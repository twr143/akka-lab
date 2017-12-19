package streaming

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future
import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global
/*
created by Ilya Volynin at 18.12.17
*/
object hello {
  implicit val actorSystem = ActorSystem("akka-streams-example")
  implicit val materializer = ActorMaterializer()
  def main(args: Array[String]): Unit = {
      Source.single("Hello world")
        .map(s => s.toUpperCase())
        .runForeach(println)
    .onComplete {
      case Success(Done) =>
        println("Stream finished successfully.")
        actorSystem.terminate()
      case Failure(e) =>
        println(s"Stream failed with $e")
    }
  }
}
