package kafka.ser_on.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._


/**
  * Created by Ilya Volynin on 13.09.2018 at 14:27.
  */
object Model {


  sealed trait SerializationBeanJsoniterBase
  case class SerializationBeanJsoniter(first: String, second: Int, third: java.time.OffsetDateTime) extends SerializationBeanJsoniterBase
  case class SerializationBeanJsoniter2(first: String, second: Int, third: java.time.OffsetDateTime, fourth: Int) extends SerializationBeanJsoniterBase
  implicit val codec: JsonValueCodec[SerializationBeanJsoniterBase] = JsonCodecMaker.make[SerializationBeanJsoniterBase](CodecMakerConfig())
//  implicit val codec2: JsonValueCodec[SerializationBeanJsoniter2] = JsonCodecMaker.make[SerializationBeanJsoniter2](CodecMakerConfig())

}
