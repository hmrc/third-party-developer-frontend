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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import org.joda.time.DateTime
import play.api.test.FakeRequest
import uk.gov.hmrc.apiplatform.modules.uplift.controllers.UpliftJourneySwitch
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.ApplicationBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.TermsOfUseServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{HmrcSpec, LocalUserIdTracker}

class TermsOfUseVersionServiceSpec extends HmrcSpec with ApplicationBuilder with LocalUserIdTracker{
  trait Setup extends TermsOfUseServiceMock {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val upliftJourneySwitch = mock[UpliftJourneySwitch]
    val request = FakeRequest()
    val email = "test@example.com"
    val application = buildApplication(email)
    val underTest = new TermsOfUseVersionService(upliftJourneySwitch, termsOfUseServiceMock)

    def givenUpliftJourneySwitchIsOff = when(upliftJourneySwitch.shouldUseV2(*)).thenReturn(false)
    def givenUpliftJourneySwitchIsOn = when(upliftJourneySwitch.shouldUseV2(*)).thenReturn(true)
  }

  "getLatest" should {
    "return v1.2 if uplift journey switch is off" in new Setup {
      givenUpliftJourneySwitchIsOff
      val result = underTest.getLatest()(request)
      result shouldBe TermsOfUseVersion.V1_2
    }
    "return v2.0 if uplift journey switch is on" in new Setup {
      givenUpliftJourneySwitchIsOn
      val result = underTest.getLatest()(request)
      result shouldBe TermsOfUseVersion.V2_0
    }
  }

  "getForApplication" should {
    "return latest ToU version if uplift journey switch is on and no ToU agreements found" in new Setup {
      givenUpliftJourneySwitchIsOn
      returnAgreementDetails()

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.latest
    }
    "return ToU version 1.2 if uplift journey switch is off and application has no CheckInformation" in new Setup {
      givenUpliftJourneySwitchIsOff
      returnAgreementDetails()

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.V1_2
    }
    "return latest ToU version if uplift journey switch is on and application has unrecognised version value" in new Setup {
      givenUpliftJourneySwitchIsOff
      returnAgreementDetails(TermsOfUseAgreementDetails(email, None, DateTime.now, "bad.version"))

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.V1_2
    }
    "return ToU version v1.2 if uplift journey switch is off and application has unrecognised version value in CheckInformation" in new Setup {
      givenUpliftJourneySwitchIsOn
      returnAgreementDetails(TermsOfUseAgreementDetails(email, None, DateTime.now, "bad.version"))

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.latest
    }

    "return correct ToU version if valid value is present in CheckInformation" in new Setup {
      returnAgreementDetails(
        TermsOfUseAgreementDetails(email, None, DateTime.now, "1.0"),
        TermsOfUseAgreementDetails(email, None, DateTime.now, "1.2"),
        TermsOfUseAgreementDetails(email, None, DateTime.now, "2.0")
      )
      val result = underTest.getForApplication(application)(request)
      result shouldBe TermsOfUseVersion.V2_0
    }
  }

}
