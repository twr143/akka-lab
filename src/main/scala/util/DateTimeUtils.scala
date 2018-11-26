package util
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
  * Created by Ilya Volynin on 26.11.2018 at 4:37.
  */
object DateTimeUtils {
  implicit val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def currentODT: String = OffsetDateTime.now().format(formatter)

}
