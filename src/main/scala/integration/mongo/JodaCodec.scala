package integration.mongo
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.joda.time.DateTime

/**
  * Created by Ilya Volynin on 17.03.2019 at 21:23.
  */
class JodaCodec extends Codec[DateTime] {

  override def decode(bsonReader: BsonReader, decoderContext: DecoderContext): DateTime = new DateTime(bsonReader.readDateTime())

  override def encode(bsonWriter: BsonWriter, t: DateTime, encoderContext: EncoderContext): Unit = bsonWriter.writeDateTime(t.getMillis)

  override def getEncoderClass: Class[DateTime] = classOf[DateTime]
}
