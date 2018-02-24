package deathWatch

/*
created by Ilya Volynin at 25.01.18
*/
package actortests.deathwatch

import akka.actor._

class Kenny extends Actor {
  def receive = {
    case "exc" => throw new Exception("exc happened!")
    case _ => println("Kenny received a message")
  }
}

class Parent(kenny: ActorRef) extends Actor {
  // start Kenny as a child, then keep an eye on it
  context.watch(kenny)
  def receive = {
    case Terminated(kenny) => println("OMG, they killed Kenny")
    case _ => println("Parent received a message")
  }
}
object Parent{
  def props(child: ActorRef): Props = Props(new Parent(child))
}

object DeathWatchTest extends App {

  // create the ActorSystem instance
  val system = ActorSystem("DeathWatchTest")

  // create the Parent that will create Kenny
  val kenny = system.actorOf(Props[Kenny], name = "Kenny")
  val parent = system.actorOf(Parent.props(kenny), name = "Parent")
  val parent2 = system.actorOf(Parent.props(kenny), name = "Parent2")

  // lookup kenny, then kill it
//  kenny ! "exc"
  kenny ! Status.Failure(new Exception("exc happened!"))
  Thread.sleep(1000)
  println("calling system.shutdown")
  system.terminate()
}