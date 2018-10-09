package motiv.evoTest
import java.util.UUID
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import scala.collection.mutable.{ListBuffer, MutableList}

/**
  * Created by Ilya Volynin on 02.10.2018 at 16:53.
  */
object Model {

  case class Table(id: Int, name: String, participants: Int)

  sealed trait Incoming

  sealed trait Outgoing

  case class Login(username: String, password: String) extends Incoming

  case class Ping(seq: Int) extends Incoming

  case object SubscribeTables extends Incoming

  case object UnsubscribeTables extends Incoming

  case class AddTable(table: Table, after_id: Int) extends Incoming

  case class UpdateTable(table: Table) extends Incoming

  case class RemoveTable(id: Int) extends Incoming

  case object QueryChanges extends Incoming

  case class LoginSuccessful(usertype: String, reqId: UUID) extends Outgoing

  case class LoginFailed(login: String) extends Outgoing

  case class InvalidBody(msg: String) extends Outgoing

  case class GeneralException(msg: String) extends Outgoing

  case class Pong(seq: Int) extends Outgoing

  case object UnsubscribedFromTables extends Outgoing

  case class TableAdded(after_id: Int, table: Table) extends Outgoing

  case class TableUpdated(table: Table) extends Outgoing

  case class TableRemoved(id: Int) extends Outgoing

  case class UpdateFailed(table: Table) extends Outgoing

  case class RemoveFailed(id: Int) extends Outgoing

  case class TableList(tables: List[Table]) extends Outgoing

  case object NotAuthorized extends Outgoing

  case object NotSubscribed extends Outgoing

  case class Changes(c: ListBuffer[String]) extends Outgoing

  implicit val codecIn: JsonValueCodec[Incoming] = JsonCodecMaker.make[Incoming](CodecMakerConfig(adtLeafClassNameMapper =
    (JsonCodecMaker.simpleClassName _).andThen(JsonCodecMaker.enforce_snake_case), discriminatorFieldName = "$type"))

  implicit val codecOut: JsonValueCodec[Outgoing] = JsonCodecMaker.make[Outgoing](CodecMakerConfig(adtLeafClassNameMapper =
    (JsonCodecMaker.simpleClassName _).andThen(JsonCodecMaker.enforce_snake_case), discriminatorFieldName = "$type"))
}
