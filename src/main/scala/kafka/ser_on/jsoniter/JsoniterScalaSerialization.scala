package kafka.ser_on.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.ovoenergy.kafka.serialization.core._
import org.apache.kafka.common.serialization.{Deserializer => KafkaDeserializer, Serializer => KafkaSerializer}

/**
  * Created by Ilya Volynin on 13.09.2018 at 14:42.
  */
object JsoniterScalaSerialization {


    def jsoniterScalaSerializer[T: JsonValueCodec](config: WriterConfig = WriterConfig()): KafkaSerializer[T] =
      serializer((_, data) => writeToArray[T](data, config))

    def jsoniterScalaDeserializer[T: JsonValueCodec](config: ReaderConfig = ReaderConfig()): KafkaDeserializer[T] =
      deserializer((_, data) => readFromArray(data, config))

}
