package groupBy
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random
/**
  * Created by Ilya Volynin on 23.09.2018 at 18:16.
  */
object groupByEntry1 {
  def main(args: Array[String]): Unit = {
    implicit val as = ActorSystem()
    implicit val mat = ActorMaterializer()
    val f = Source
      .tick(0 second, 50 millis, "").take(200).
      map {
        _ => if (Random.nextBoolean) (1, s"A") else (2, s"B")
      }
      .groupBy(10, _._1)
      // how to aggregate grouped elements here for two seconds?
      .scan(Seq[String]()) { (x, y) => x ++ Seq(y._2) }.filter(_.length % 20 == 0)//.takeWhile(_.length < 100)
      .mergeSubstreams
      .runForeach(println)
    try Await.result(f, 60.minutes)
    finally as.terminate()
  }
}
