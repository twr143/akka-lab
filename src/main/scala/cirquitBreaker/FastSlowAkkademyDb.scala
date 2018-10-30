package cirquitBreaker
import akka.actor.{Actor, ActorSystem, Props, Status}
import akka.event.Logging
import akka.pattern.CircuitBreaker
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern._
import akka.stream.ActorMaterializer
import util.StreamWrapperApp
import scala.collection.mutable.HashMap
import scala.concurrent.{Await, ExecutionContext, Future}

class FastSlowAkkademyDb extends Actor {

  val map = new HashMap[String, Object]

  val log = Logging(context.system, this)

  override def receive: Receive = {
    case SetRequest(key, value) =>
      log.info("received SetRequest - key: {} value: {}", key, value)
      map.put(key, value)
      sender() ! Status.Success
    case GetRequest(key) =>
      Thread.sleep(70)
      respondToGet(key)
    case o => Status.Failure(new ClassNotFoundException)
  }

  def respondToGet(key: String): Unit = {
    val response: Option[Object] = map.get(key)
    response match {
      case Some(x) => sender() ! x
      case None => sender() ! Status.Failure(KeyNotFoundException(key))
    }
  }
}

object FSDbEntry extends StreamWrapperApp {

  def body()(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Future[Any] = {
    implicit val timeout: Timeout = Timeout(100 millis)
    val db = as.actorOf(Props[FastSlowAkkademyDb])
    val log = Logging(as, db)
    val breaker =
      new CircuitBreaker(as.scheduler,
        maxFailures = 10,
        callTimeout = 1 seconds,
        resetTimeout = 3 seconds).
        onOpen(println("circuit breaker opened!")).
        onClose(println("circuit breaker closed!")).
        onHalfOpen(log.info("circuit breaker half-open"))
    Await.result(db ? SetRequest("key", "value"), 2 seconds)
    (1 to 100).toStream.foreach { _ =>
      Thread.sleep(50)
      // put timeline: 50,100,150
      // exec timeline: 120,190,260
      // the third diff: 260-150>100 -> askTimeout
      breaker.withCircuitBreaker(db ? GetRequest("key"))
        .map(x => "got it: " + x).recover {
        case t ⇒ "error: " + t.toString
      }.foreach(log.info)
    }
    Future()
  }
}

case class GetRequest(str: String)

case class SetRequest(str: String, str1: String)

case class KeyNotFoundException(str: String) extends Exception

