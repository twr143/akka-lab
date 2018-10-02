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

  case class WelcomeResponse(login: String, message: String) extends Outgoing

  case class DenyResponse(login: String, message: String) extends Outgoing

  implicit val codecCreds: JsonValueCodec[Creds] = JsonCodecMaker.make[Creds](CodecMakerConfig())

  implicit val codecIn: JsonValueCodec[Incoming] = JsonCodecMaker.make[Incoming](CodecMakerConfig())

  implicit val codecOut: JsonValueCodec[Outgoing] = JsonCodecMaker.make[Outgoing](CodecMakerConfig())
}
