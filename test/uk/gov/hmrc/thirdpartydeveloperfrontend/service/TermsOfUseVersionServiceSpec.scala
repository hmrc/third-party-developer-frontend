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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.ApplicationBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.TermsOfUseServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{HmrcSpec, LocalUserIdTracker}

class TermsOfUseVersionServiceSpec extends HmrcSpec with ApplicationBuilder with LocalUserIdTracker {

  trait Setup extends TermsOfUseServiceMock {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val request     = FakeRequest()
    val email       = "test@example.com".toLaxEmail
    val application = buildApplication(email)
    val underTest   = new TermsOfUseVersionService(termsOfUseServiceMock)
  }

  "getLatest" should {
    "return NEW_JOURNEY if uplift journey switch is on" in new Setup {
      val result = underTest.getLatest()(request)
      result shouldBe TermsOfUseVersion.NEW_JOURNEY
    }
  }

  "getForApplication" should {
    "return latest ToU version if uplift journey switch is on and no ToU agreements found" in new Setup {
      returnAgreementDetails()

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.latest
    }

    "return ToU version NEW_JOURNEY if uplift journey switch is on and application has unrecognised version value in CheckInformation" in new Setup {
      returnAgreementDetails(TermsOfUseAgreementDetails(email, None, instant, Some("bad.version")))

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.NEW_JOURNEY
    }

    "return correct ToU version if valid value is present in CheckInformation" in new Setup {
      returnAgreementDetails(
        TermsOfUseAgreementDetails(email, None, instant, Some("1.0")),
        TermsOfUseAgreementDetails(email, None, instant, Some("1.2")),
        TermsOfUseAgreementDetails(email, None, instant, Some("2.0"))
      )
      val result = underTest.getForApplication(application)(request)
      result shouldBe TermsOfUseVersion.NEW_JOURNEY
    }
  }

}
