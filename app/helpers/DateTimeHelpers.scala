package helpers

import org.joda.time.LocalDate

object DateTimeHelpers {
  def parseLocalDate(value: Option[String]): Option[LocalDate] = {
    value.flatMap((configValue: String) => {
      configValue.trim match {
        case "" => None
        case _ => Some(LocalDate.parse(configValue))
      }
    })
  }
}
