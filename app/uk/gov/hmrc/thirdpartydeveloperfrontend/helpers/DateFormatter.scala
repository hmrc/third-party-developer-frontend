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
import java.time.{Clock, Instant, LocalDate, ZoneOffset}

import uk.gov.hmrc.apiplatform.modules.common.domain.services.DateTimeHelper.{InstantConversionSyntax, LocalDateConversionSyntax}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow

object DateFormatter {
  val shortFormatter: DateTimeFormatter               = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneOffset.UTC)
  val standardFormatter: DateTimeFormatter            = DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneOffset.UTC)
  val twoDigitDayFormatter: DateTimeFormatter         = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC)
  val twoDigitDayWithTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm").withZone(ZoneOffset.UTC)
  val initialLastAccessDate                           = LocalDate.of(2019, 6, 25).asInstant // scalastyle:ignore magic.number

  def formatDateWithShortPattern(dateTime: Instant): String = {
    shortFormatter.format(dateTime)
  }

  def formatDate(dateTime: Instant): String = {
    standardFormatter.format(dateTime)
  }

  def formatTwoDigitDay(dateTime: Instant): String = {
    twoDigitDayFormatter.format(dateTime)
  }

  def formatTwoDigitDayWithTime(dateTime: Instant): String = {
    twoDigitDayWithTimeFormatter.format(dateTime)
  }

  def formatLastAccessDate(maybeLastAccess: Option[Instant], createdOnDate: Instant, aClock: Clock): Option[String] = {
    val clk = new ClockNow { val clock = aClock }

    def formatDateValue(lastAccessDate: Instant) = {
      if (ChronoUnit.DAYS.between(initialLastAccessDate.asLocalDate, lastAccessDate.asLocalDate) > 0) {
        standardFormatter.format(lastAccessDate)
      } else {
        s"more than ${ChronoUnit.MONTHS.between(lastAccessDate.asLocalDate, clk.instant().asLocalDate)} months ago"
      }
    }
    maybeLastAccess
      .filterNot(lastAccessDate => ChronoUnit.SECONDS.between(createdOnDate, lastAccessDate) == 0)
      .map(formatDateValue)
  }
}
