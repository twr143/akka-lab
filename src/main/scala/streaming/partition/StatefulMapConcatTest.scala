package streaming.partition
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import scala.util.Random._
import scala.math.abs
import scala.concurrent.ExecutionContext.Implicits.global
/**
  * Created by Ilya Volynin on 15.06.2018 at 18:38.
  */
object StatefulMapConcatTest extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  //encapsulating your input
  case class IdentValue(id: Int, value: String)
  //some random generated input
  val identValues = List.fill(5)(IdentValue(abs(nextInt()) % 5, "valueHere"))
  var ids = Set.empty[Int]
  val stateFlow = Flow[IdentValue].statefulMapConcat { () =>
    //state with already processed ids
    println("initial block in smc")
    identValue =>
      ids = ids + identValue.id
        Set(identValue)
  }
  Source(identValues)
    .via(stateFlow)
    .runWith(Sink.seq)
    .onSuccess { case identValue => println(identValue); println(ids); system.terminate() }
}
