/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.DateFormatter.initialLastAccessDate
import org.scalatest.BeforeAndAfterAll
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

import java.time.LocalDateTime

class DateFormatterSpec extends AsyncHmrcSpec with BeforeAndAfterAll {
  val FixedTimeNow: LocalDateTime = new LocalDateTime("2019-09-01T00:30:00.000")
  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  "formatDateWithShortPattern" should {
    "use short date format" in {
      val dateTime = new LocalDateTime(2019, 1, 1, 0, 0) // scalastyle:ignore magic.number
      DateFormatter.formatDateWithShortPattern(dateTime) shouldBe "1 Jan 2019"
    }
  }

  "formatDate" should {
    "use long date format" in {
      val dateTime = new LocalDateTime(2019, 1, 1, 0, 0) // scalastyle:ignore magic.number
      DateFormatter.formatDate(dateTime) shouldBe "1 January 2019"
    }
  }

  "formatLastAccessDate" should {
    "use long date format for dates after the initial last access date" in {
      val lastAccessDate = initialLastAccessDate.plusDays(1)
      val createdOnDate = lastAccessDate.minusHours(1)
      DateFormatter.formatLastAccessDate(Some(lastAccessDate), createdOnDate) shouldBe Some("26 June 2019")
    }

    "use inexact format for dates before the initial last access date" in {
      val lastAccessDate = initialLastAccessDate.minusDays(1)
      val createdOnDate = lastAccessDate.minusHours(1)
      DateFormatter.formatLastAccessDate(Some(lastAccessDate), createdOnDate) shouldBe Some("more than 2 months ago")
    }

    "use inexact format for dates on the initial last access date" in {
      val lastAccessDate = initialLastAccessDate.plusHours(3)
      val createdOnDate = lastAccessDate.minusHours(1)
      DateFormatter.formatLastAccessDate(Some(lastAccessDate), createdOnDate) shouldBe Some("more than 2 months ago")
    }

    "return None if the last access date is the same as the created date" in {
      val createdDate = initialLastAccessDate.plusHours(3)
      DateFormatter.formatLastAccessDate(Some(createdDate), createdDate) shouldBe None
    }

    "return None if the last access date None" in {
      val createdDate = initialLastAccessDate.plusHours(3)
      DateFormatter.formatLastAccessDate(None, createdDate) shouldBe None
    }

    "return None if the last access date is within a second of the created date" in {
      val createdDate = initialLastAccessDate.plusHours(3)
      DateFormatter.formatLastAccessDate(Some(createdDate.plusMillis(900)), createdDate) shouldBe None // scalastyle:ignore magic.number
      DateFormatter.formatLastAccessDate(Some(createdDate.minusMillis(900)), createdDate) shouldBe None // scalastyle:ignore magic.number
    }
  }
}
