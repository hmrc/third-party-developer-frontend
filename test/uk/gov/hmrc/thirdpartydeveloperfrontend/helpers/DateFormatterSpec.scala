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

import java.time.temporal.ChronoUnit.{DAYS, HOURS, MILLIS}
import java.time.{Instant, LocalDate, LocalDateTime}

import org.scalatest.BeforeAndAfterAll

import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.DateFormatter.initialLastAccessDate
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.InstantConversion.{LocalDateSyntax, LocalDateTimeSyntax}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class DateFormatterSpec extends AsyncHmrcSpec with BeforeAndAfterAll with FixedClock {
  val fixedTimeNow: Instant = Instant.parse("2019-09-01T00:30:00.000Z")

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  "formatDateWithShortPattern" should {
    "use short date format" in {
      val dateTime = LocalDate.of(2019, 1, 1).asInstant // scalastyle:ignore magic.number
      DateFormatter.formatDateWithShortPattern(dateTime) shouldBe "1 Jan 2019"
    }
  }

  "formatDate" should {
    "use long date format" in {
      val dateTime = LocalDate.of(2019, 1, 1).asInstant // scalastyle:ignore magic.number
      DateFormatter.formatDate(dateTime) shouldBe "1 January 2019"
    }
  }

  "formatTwoDigitDay" should {
    "use long date format" in {
      val dateTime = LocalDate.of(2019, 1, 1).asInstant // scalastyle:ignore magic.number
      DateFormatter.formatTwoDigitDay(dateTime) shouldBe "01 January 2019"
    }
  }

  "formatTwoDigitDayWithTime" should {
    "use long date format" in {
      val dateTime = LocalDateTime.of(2019, 1, 1, 2, 3).asInstant // scalastyle:ignore magic.number
      DateFormatter.formatTwoDigitDayWithTime(dateTime) shouldBe "01 January 2019 02:03"
    }
  }

  "formatLastAccessDate" should {
    "use long date format for dates after the initial last access date" in {
      val lastAccessDate = initialLastAccessDate.plus(1, DAYS)
      val createdOnDate  = lastAccessDate.minus(1, HOURS)
      DateFormatter.formatLastAccessDate(Some(lastAccessDate), createdOnDate, clock) shouldBe Some("26 June 2019")
    }

    "use inexact format for dates before the initial last access date" in {
      val lastAccessDate = initialLastAccessDate.minus(1, DAYS)
      val createdOnDate  = lastAccessDate.minus(1, HOURS)
      DateFormatter.formatLastAccessDate(Some(lastAccessDate), createdOnDate, clock) shouldBe Some("more than 6 months ago")
    }

    "use inexact format for dates on the initial last access date" in {
      val lastAccessDate = initialLastAccessDate.plus(3, HOURS)
      val createdOnDate  = lastAccessDate.minus(1, HOURS)
      DateFormatter.formatLastAccessDate(Some(lastAccessDate), createdOnDate, clock) shouldBe Some("more than 6 months ago")
    }

    "return None if the last access date is the same as the created date" in {
      val createdDate = initialLastAccessDate.plus(3, HOURS)
      DateFormatter.formatLastAccessDate(Some(createdDate), createdDate, clock) shouldBe None
    }

    "return None if the last access date None" in {
      val createdDate = initialLastAccessDate.plus(3, HOURS)
      DateFormatter.formatLastAccessDate(None, createdDate, clock) shouldBe None
    }

    "return None if the last access date is within a second of the created date" in {
      val createdDate = initialLastAccessDate.plus(3, HOURS)
      DateFormatter.formatLastAccessDate(Some(createdDate.plus(900, MILLIS)), createdDate, clock) shouldBe None  // scalastyle:ignore magic.number
      DateFormatter.formatLastAccessDate(Some(createdDate.minus(900, MILLIS)), createdDate, clock) shouldBe None // scalastyle:ignore magic.number
    }
  }
}
