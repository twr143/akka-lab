/*
 * Copyright 2017 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package flow
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, ThrottleMode}
import ch.qos.logback.classic.Logger
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

/**
  * Prints "Learn you Akka Streams for great good!" in fancy ways.
  */
object ZipEntry extends StreamWrapperApp2 {

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val sevenLines =
      Source.fromIterator(() => Iterator.from(0))
        .take(7)
    val toCharsIndented =
      Flow[Int]
        .zipWithIndex
        .mapConcat {
          case (s, n) =>
            f"${s.toString.padTo(n.toInt + 1, ' ').reverse}%n"
        }
    val printThrottled =
      Flow[Char]
        .throttle(42, 1.second)
        .toMat(Sink.foreach(print))(Keep.right)
    sevenLines
      .via(toCharsIndented)
      .toMat(printThrottled)(Keep.right)
      .run()
  }
}