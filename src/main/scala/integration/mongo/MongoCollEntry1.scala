package integration.mongo
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.mongodb.DocumentUpdate
import akka.stream.alpakka.mongodb.scaladsl.{MongoSink, MongoSource}
import akka.stream.scaladsl.{Sink, Source}
import ch.qos.logback.classic.Logger
import com.mongodb.reactivestreams.client.MongoClients
import org.bson.codecs.configuration.CodecRegistries._
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.{Filters, Updates}

/**
  * Created by Ilya Volynin on 14.03.2019 at 11:09.
  */
object MongoCollEntry1 extends StreamWrapperApp2 {

  case class Number(_id: Int, value: Int)

  val codecRegistry = fromRegistries(fromProviders(classOf[Number]), DEFAULT_CODEC_REGISTRY)

  private val client = MongoClients.create("mongodb://localhost:27017")

  private val db = client.getDatabase("MongoSourceSpec")

  private val db2 = client.getDatabase("MongoSourceSpec2")

  private val numbersColl = db
    .getCollection("numbers", classOf[Number])
    .withCodecRegistry(codecRegistry)

  private val numbersColl2 = db2
    .getCollection("numbers", classOf[Number])
    .withCodecRegistry(codecRegistry)

  override def body(args: Array[String])(implicit as: ActorSystem,
                                         mat: ActorMaterializer, ec: ExecutionContext,
                                         logger: Logger): Future[Any] = {
    if (args.length != 1)
      return Future.failed(new IllegalArgumentException("please provide an action to do with mongoDb"))
    val action = args(0)
    if (action == "create") {
      val source = Source(1 to 10).map(i => Number(i, i))
      source.runWith(MongoSink.insertOne(numbersColl))
    } else if (action == "query") {
      val source: Source[Number, NotUsed] =
        MongoSource(numbersColl.find(Filters.eq("_id", 3), classOf[Number]))
      source.runForeach(n => logger.warn(n.toString))
    } else if (action == "update") {
      val source = Source.single(5).map(
        i => DocumentUpdate(filter = Filters.eq("_id", i),
          update = Updates.set("value", i * -1))
      )
      source.runWith(MongoSink.updateOne(numbersColl))
    } else if (action == "delete") {
      val source = Source(1 to 10).map(i => Filters.eq("_id", i))
      source.runWith(MongoSink.deleteMany(numbersColl))
    } else if (action == "createAndTransfer") {
      val source = Source(1 to 10).map(i => Number(i, i))
      val source2: Source[Number, NotUsed] =
        MongoSource(numbersColl.find(classOf[Number]))
      for {
        _ <- source.runWith(MongoSink.insertOne(numbersColl))
        r <- source2.runWith(MongoSink.insertOne(numbersColl2))
      } yield r
    }
    else {
      Future.failed(new IllegalArgumentException(s" illegal action $action"))
    }
  }
}
