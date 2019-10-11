package routing
import akka.actor.{Actor, ActorLogging}

/**
  * Created by Ilya Volynin on 11.10.2019 at 10:08.
  */

class RoundRobin extends Actor with ActorLogging {
  override def receive = {
    case msg: String => log.info(s" I am ${self.path.name}")
    case _ => log.info("Unknown message ")
  }
}