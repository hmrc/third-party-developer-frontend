/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package helpers

import org.joda.time.DateTime
import org.joda.time.Months.monthsBetween
import org.joda.time.Seconds.secondsBetween
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import uk.gov.hmrc.time.DateTimeUtils.{daysBetween, now}

object DateFormatter {
  val shortFormatter: DateTimeFormatter = DateTimeFormat.forPattern("d MMM yyyy")
  val standardFormatter: DateTimeFormatter = DateTimeFormat.forPattern("d MMMM yyyy")
  val initialLastAccessDate = new DateTime(2019, 6, 25, 0, 0) // scalastyle:ignore magic.number

  def formatDateWithShortPattern(dateTime: DateTime): String = {
    shortFormatter.print(dateTime)
  }

  def formatDate(dateTime: DateTime): String = {
    standardFormatter.print(dateTime)
  }

  def formatLastAccessDate(lastAccessDate: DateTime, createdOnDate: DateTime): Option[String] = {
    if (secondsBetween(createdOnDate, lastAccessDate).getSeconds == 0) {
      None
    } else if (daysBetween(initialLastAccessDate.toLocalDate, lastAccessDate.toLocalDate) > 0) {
      Some(standardFormatter.print(lastAccessDate))
    } else {
      Some(s"more than ${monthsBetween(lastAccessDate, now).getMonths} months ago")
    }
  }
}
