package streaming
import org.joda
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

/*
created by Ilya Volynin at 18.12.17
*/
object AkkaStreamZipTest extends FlatSpec with Matchers {

  // Akka streams specific
  import akka.actor.ActorSystem
  import akka.stream.ActorMaterializer
  import akka.stream.scaladsl.{Flow, GraphDSL, Source, Zip}

  implicit val actorSystem = ActorSystem("stream-poc")

  implicit val materializer = ActorMaterializer()

}
