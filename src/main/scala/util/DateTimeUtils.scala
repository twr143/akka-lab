package util
import java.time.{OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Date

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

/**
  * Created by Ilya Volynin on 26.11.2018 at 4:37.
  */
object DateTimeUtils {

  implicit val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  val jodaFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  def currentODT: String = OffsetDateTime.now().format(formatter)

  implicit def fromDateTime(date: DateTime): String = jodaFormatter.print(date)


}
