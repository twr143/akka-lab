package colin
import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.Future
import scala.util.{Failure, Random, Success}
object CollectMapConcat {
  val random = new Random()
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  def main(args: Array[String]): Unit = {
    /* Source(1 to 1000)
       .collect({
         case x if x % 100 == 1 => x * 2
       })
       .runForeach(println)
      */
    /* Source(1 to 1000)
       .mapConcat(x => if (x % 100 == 1) x * 2 :: Nil else Nil)
       .runForeach(println)
      */
    val strings = List(
      """hello
         world
         test
         this""",
      """foo
         bar
         baz""",
      """foo
         bar
         baz"""
    )
    /*Source(strings).
      mapConcat(_.split("\n").toList).map(_.trim).
      runForeach(println).onComplete(_ => system.terminate())(system.dispatcher)*/

    Source(strings).
      mapConcat(_.split("\n").toList).map(_.trim).
      runForeach(println).onComplete(_ => system.terminate())(system.dispatcher)
  }
}
