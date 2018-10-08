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

  case class login(username: String, password: String) extends Incoming

  case class ping(seq: Int) extends Incoming

  case class subscribe_tables() extends Incoming

  case class unsubscribe_tables() extends Incoming

  case class add_table(table: Table, after_id: Int) extends Incoming

  case class update_table(table: Table) extends Incoming

  case class remove_table(id: Int) extends Incoming

  case class query_changes() extends Incoming

  case class login_successful(usertype: String, reqId: UUID) extends Outgoing

  case class login_failed(login: String) extends Outgoing

  case class InvalidBody(msg: String) extends Outgoing

  case class GeneralException(msg: String) extends Outgoing

  case class pong(seq: Int) extends Outgoing

  case object unsubscribed_from_tables extends Outgoing

  case class table_added(after_id: Int, table: Table) extends Outgoing

  case class table_updated(table: Table) extends Outgoing

  case class table_removed(id: Int) extends Outgoing

  case class update_failed(table: Table) extends Outgoing

  case class remove_failed(id: Int) extends Outgoing

  case class table_list(tables: List[Table]) extends Outgoing

  case object not_authorized extends Outgoing

  case object not_subscribed extends Outgoing

  case class changes(c: ListBuffer[String]) extends Outgoing

  implicit val codecIn: JsonValueCodec[Incoming] = JsonCodecMaker.make[Incoming](CodecMakerConfig(discriminatorFieldName = "$type"))

  implicit val codecOut: JsonValueCodec[Outgoing] = JsonCodecMaker.make[Outgoing](CodecMakerConfig(discriminatorFieldName = "$type"))

}
