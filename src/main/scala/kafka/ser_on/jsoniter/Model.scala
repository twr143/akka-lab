package kafka.ser_on.jsoniter


import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._


/**
  * Created by Ilya Volynin on 13.09.2018 at 14:27.
  */
object Model {



  case class SerializationBeanJsoniter(first: String, second: Int, third: java.time.OffsetDateTime)
  implicit val codec: JsonValueCodec[SerializationBeanJsoniter] = JsonCodecMaker.make[SerializationBeanJsoniter](CodecMakerConfig())

}
