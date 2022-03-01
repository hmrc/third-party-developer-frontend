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
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{CheckInformation, TermsOfUseAgreement}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{HmrcSpec, LocalUserIdTracker}

class TermsOfUseVersionServiceSpec extends HmrcSpec with ApplicationBuilder with LocalUserIdTracker{
  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val upliftJourneySwitch = mock[UpliftJourneySwitch]
    val request = FakeRequest()
    val email = "test@example.com"
    val underTest = new TermsOfUseVersionService(upliftJourneySwitch)

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
    "return latest ToU version if uplift journey switch is on and application has no CheckInformation" in new Setup {
      givenUpliftJourneySwitchIsOn
      val application = buildApplication(email).copy(checkInformation = None)

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.latest
    }
    "return ToU version 1.2 if uplift journey switch is off and application has no CheckInformation" in new Setup {
      givenUpliftJourneySwitchIsOff
      val application = buildApplication(email).copy(checkInformation = None)

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.V1_2
    }
    "return latest ToU version if uplift journey switch is on and application has no TermsOfUseAgreements in CheckInformation" in new Setup {
      givenUpliftJourneySwitchIsOn
      val application = buildApplication(email).copy(checkInformation = Some(CheckInformation(termsOfUseAgreements = List.empty)))

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.latest
    }
    "return ToU version v1.2 if uplift journey switch is off and application has no TermsOfUseAgreements in CheckInformation" in new Setup {
      givenUpliftJourneySwitchIsOff
      val application = buildApplication(email).copy(checkInformation = Some(CheckInformation(termsOfUseAgreements = List.empty)))

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.V1_2
    }
    "return latest ToU version if uplift journey switch is on and application has unrecognised version value in CheckInformation" in new Setup {
      givenUpliftJourneySwitchIsOff
      val application = buildApplication(email).copy(checkInformation = Some(CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(email, DateTime.now(), "bad.version")))))

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.V1_2
    }
    "return ToU version v1.2 if uplift journey switch is off and application has unrecognised version value in CheckInformation" in new Setup {
      givenUpliftJourneySwitchIsOn
      val application = buildApplication(email).copy(checkInformation = Some(CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(email, DateTime.now(), "bad.version")))))

      val result = underTest.getForApplication(application)(request)

      result shouldBe TermsOfUseVersion.latest
    }

    "return correct ToU version if valid value is present in CheckInformation" in new Setup {
      val application = buildApplication(email).copy(checkInformation = Some(CheckInformation(termsOfUseAgreements = List(
        TermsOfUseAgreement(email, DateTime.now(), "1.0"),
        TermsOfUseAgreement(email, DateTime.now(), "1.1"),
        TermsOfUseAgreement(email, DateTime.now(), "2.0")
      ))))
      val result = underTest.getForApplication(application)(request)
      result shouldBe TermsOfUseVersion.V2_0
    }
  }

}
