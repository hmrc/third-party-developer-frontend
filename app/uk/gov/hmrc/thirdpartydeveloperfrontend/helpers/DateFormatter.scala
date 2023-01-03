/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.helpers

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Clock, LocalDateTime}

object DateFormatter {
  val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
  val standardFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  val initialLastAccessDate = LocalDateTime.of(2019, 6, 25, 0, 0) // scalastyle:ignore magic.number

  def formatDateWithShortPattern(dateTime: LocalDateTime): String = {
    shortFormatter.format(dateTime)
  }

  def formatDate(dateTime: LocalDateTime): String = {
    standardFormatter.format(dateTime)
  }

  def formatLastAccessDate(maybeLastAccess: Option[LocalDateTime], createdOnDate: LocalDateTime, clock: Clock): Option[String] = {
    def formatDateValue(lastAccessDate: LocalDateTime) = {
     if (ChronoUnit.DAYS.between(initialLastAccessDate.toLocalDate, lastAccessDate.toLocalDate) > 0) {
        standardFormatter.format(lastAccessDate)
      } else {
        s"more than ${ChronoUnit.MONTHS.between(lastAccessDate.toLocalDate, LocalDateTime.now(clock).toLocalDate)} months ago"
      }
    }
    maybeLastAccess
      .filterNot(lastAccessDate => ChronoUnit.SECONDS.between(createdOnDate, lastAccessDate) == 0)
      .map(formatDateValue)
  }
}
