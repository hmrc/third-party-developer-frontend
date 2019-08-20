/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.service

import config.ApplicationConfig
import org.joda.time.{Duration, Instant, LocalDate}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import service.MfaMandateService

class MfaMandateServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures {

  val dateInThePast: LocalDate = Instant.now().minus(Duration.standardDays(1L)).toDateTime().toLocalDate
  val dateInTheFuture: LocalDate = Instant.now().plus(Duration.standardDays(1L)).toDateTime().toLocalDate
  val now: LocalDate = Instant.now().toDateTime().toLocalDate

  "showAdminMfaMandateMessage" when {
    "Mfa mandate date has passed" should {
      "be false" in {
        val mockAppConfig = mock[ApplicationConfig]

        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInThePast))

        val service = new MfaMandateService(mockAppConfig)

        service.showAdminMfaMandatedMessage shouldBe false
      }
    }

    "Mfa mandate date has not passed" should {
      "be true" in {
        val mockAppConfig = mock[ApplicationConfig]
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInTheFuture))

        val service = new MfaMandateService(mockAppConfig)

        service.showAdminMfaMandatedMessage shouldBe true
      }
    }

    "Mfa mandate date is not set" should {
      "be false" in {
        val mockAppConfig = mock[ApplicationConfig]
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(None)

        val service = new MfaMandateService(mockAppConfig)

        service.showAdminMfaMandatedMessage shouldBe false
      }
    }
  }

  "daysTillAdminMfaMandate" when {
    "mfaAdminMandateDate is one day in the future" should {
      "be 1" in {
        val mockAppConfig = mock[ApplicationConfig]
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInTheFuture))

        val service = new MfaMandateService(mockAppConfig)

        service.daysTillAdminMfaMandate shouldBe Some(1)
      }
    }
  }

  "daysTillAdminMfaMandate" when {
    "mfaAdminMandateDate is now" should {
      "be 0" in {
        val mockAppConfig = mock[ApplicationConfig]
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(now))

        val service = new MfaMandateService(mockAppConfig)

        service.daysTillAdminMfaMandate shouldBe Some(0)
      }
    }
  }

  "daysTillAdminMfaMandate" when {
    "mfaAdminMandateDate is in the past" should {
      "be none" in {
        val mockAppConfig = mock[ApplicationConfig]
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInThePast))

        val service = new MfaMandateService(mockAppConfig)

        service.daysTillAdminMfaMandate shouldBe None
      }
    }
  }

  "daysTillAdminMfaMandate" when {
    "mfaAdminMandateDate is not set" should {
      "be none" in {
        val mockAppConfig = mock[ApplicationConfig]
        when(mockAppConfig.dateOfAdminMfaMandate).thenReturn(Some(dateInThePast))

        val service = new MfaMandateService(mockAppConfig)

        service.daysTillAdminMfaMandate shouldBe None
      }
    }
  }

  "parseLocalDate" when {
    "an empty date value" should {
      "parse to None" in {
        MfaMandateService.parseLocalDate(Some("")) shouldBe None
      }
    }

    "an whitespace date value" should {
      "parse to None" in {
        MfaMandateService.parseLocalDate(Some(" ")) shouldBe None
      }
    }

    "an None date value" should {
      "parse to None" in {
        MfaMandateService.parseLocalDate(None) shouldBe None
      }
    }

    "the date 2001-02-03" should {
      "parse to a local date" in {
        val year = 2001
        val month = 2
        val day = 3
        MfaMandateService.parseLocalDate(Some("2001-02-03")) shouldBe Some(new LocalDate(year, month, day))
      }
    }
  }




}
