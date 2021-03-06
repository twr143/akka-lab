package integration.mongo
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mongodb.DocumentUpdate
import akka.stream.alpakka.mongodb.scaladsl.{MongoSink, MongoSource}
import akka.stream.scaladsl.{Sink, Source}
import ch.qos.logback.classic.Logger
import com.mongodb.reactivestreams.client.MongoClients
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistries._
import org.joda.time.DateTime
import util.StreamWrapperApp2
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.{Filters, Updates}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
  * Created by Ilya Volynin on 17.03.2019 at 15:41.
  */
object ComplexCollEntry extends StreamWrapperApp2 {
  import util.DateTimeUtils._

  case class ComplexBean(value: Int, date: DateTime) {

    override def toString: String = s"[$value, ${fromDateTime(date)}]"
  }

  val codecRegistry = fromRegistries(
    fromProviders(classOf[ComplexBean]),
    CodecRegistries.fromCodecs(new JodaCodec),
    DEFAULT_CODEC_REGISTRY
  )

  private val client = MongoClients.create("mongodb://localhost:27017")

  private val db = client.getDatabase("MongoSourceSpec")

  private val complColl = db
    .getCollection("complColl", classOf[ComplexBean])
    .withCodecRegistry(codecRegistry)

  override def body(args: Array[String])(implicit as: ActorSystem,
                                         mat: ActorMaterializer, ec: ExecutionContext,
                                         logger: Logger): Future[Any] = {
    if (args.length != 1)
      return Future.failed(new IllegalArgumentException("please provide an action to do with mongoDb"))
    val action = args(0)
    val random = new Random()
    if (action == "create") {
      val source = Source(1 to 10).map(i => ComplexBean(i, new DateTime()))
      source.runWith(MongoSink.insertOne(complColl))
    } else if (action == "query") {
      val source: Source[ComplexBean, NotUsed] =
        MongoSource(complColl.find(Filters.notEqual("value", 12), classOf[ComplexBean]))
      source.runForeach(n => logger.warn(n.toString))
    } else if (action == "update") {
      val source = Source(5 to 6).map(
        i => DocumentUpdate(filter = Filters.eq("value", i),
          update = Updates.set("value", i * -1))
      )
      source.runWith(MongoSink.updateMany(complColl))
    } else if (action == "delete") {
      val source = Source(1 to 10).map(i => Filters.or(
        Filters.eq("value", i),
        Filters.eq("value", -i)))
        source.runWith(MongoSink.deleteMany(complColl))
    }
    else {
      Future.failed(new IllegalArgumentException(s" illegal action $action"))
    }
  }
}
