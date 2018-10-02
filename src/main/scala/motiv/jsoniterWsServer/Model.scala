package motiv.jsoniterWsServer
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}

/**
  * Created by Ilya Volynin on 02.10.2018 at 16:53.
  */
object Model {

  sealed trait Incoming

  sealed trait Outgoing

  case class Creds(login: String, password: String) extends Incoming

  case class WelcomeResponse(login: String) extends Outgoing

  case class DenyResponse(login: String) extends Outgoing

  case class InvalidBody(msg: String) extends Outgoing

  case class GeneralException(msg: String) extends Outgoing

  implicit val codecCreds: JsonValueCodec[Creds] = JsonCodecMaker.make[Creds](CodecMakerConfig())

  implicit val codecOut: JsonValueCodec[Outgoing] = JsonCodecMaker.make[Outgoing](CodecMakerConfig())

}
