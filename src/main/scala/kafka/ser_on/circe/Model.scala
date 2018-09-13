package kafka.ser_on.circe

import java.util.Date

import cats.syntax.either._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

/**
  * Created by Ilya Volynin on 13.09.2018 at 13:27.
  */
object Model {
  val fmt = "yyyy-MM-dd HH:mm:ss"
  def sdf(fmt: String) = new java.text.SimpleDateFormat(fmt)

  implicit val encodeDate = encodesDate(fmt)
  implicit val decodeDate: Decoder[Date] = Decoder.decodeString.emap { str =>
    Either.catchNonFatal(sdf(fmt).parse(str)).leftMap(t => s"exception ${t.getMessage} ${t.getCause}")
  }
  implicit val dec: Decoder[SerializationBean] = Decoder[SerializationBean] {
    Decoder.forProduct3("first", "second", "third")(SerializationBean.apply)
  }.map[SerializationBean](identity)


  implicit val enc: Encoder[SerializationBean] = Encoder.instance {
    case SerializationBean(f, s, t) => Json.obj(
      "first" -> f.asJson,
      "second" -> s.asJson,
      "third" -> t.asJson
    )
  }

  def encodesDate(fmt: String): Encoder[Date] = new Encoder[Date] {
    def apply(a: Date) = {
      Json.fromString(sdf(fmt).format(a))
    }
  }
  case class SerializationBean(first: String, second: Int, third: java.util.Date)

}
