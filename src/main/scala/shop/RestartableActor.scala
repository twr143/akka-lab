package shop

import akka.persistence.PersistentActor
import shop.RestartableActor.{RestartActor, RestartActorException}

trait RestartableActor extends PersistentActor {

  abstract override def receiveCommand:Receive = super.receiveCommand orElse {
    case RestartActor => throw RestartActorException
  }
}

object RestartableActor {
  case object RestartActor

  private object RestartActorException extends Exception
}
