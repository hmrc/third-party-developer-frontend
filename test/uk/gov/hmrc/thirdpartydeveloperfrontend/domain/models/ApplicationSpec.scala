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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models

import org.joda.time.DateTime
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ClientId, Environment, ImportantSubmissionData, PrivacyPolicyLocation, ResponsibleIndividual, ServerLocation, Standard, TermsAndConditionsLocation, TermsOfUseAcceptance}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.HmrcSpec

import java.time.Period

class ApplicationSpec extends HmrcSpec {
  val url = "http://example.com"
  val standardAccess = Standard()
  val importantSubmissionData = ImportantSubmissionData(
    None,
    ResponsibleIndividual(ResponsibleIndividual.Name("bob"), ResponsibleIndividual.EmailAddress("bob")),
    Set.empty[ServerLocation],
    TermsAndConditionsLocation.NoneProvided,
    PrivacyPolicyLocation.NoneProvided,
    List.empty[TermsOfUseAcceptance]
  )
  val baseApplication = Application(ApplicationId.random, ClientId("client"), "name", DateTime.now(), DateTime.now(), None, Period.ofDays(1), Environment.PRODUCTION, access = standardAccess)

  "privacy policy location" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.copy(access = Standard(privacyPolicyUrl = None))
      application.privacyPolicyUrl shouldBe None
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.copy(access = Standard(privacyPolicyUrl = Some(url)))
      application.privacyPolicyUrl shouldBe Some(url)
    }
    "be correct for new journey app when location was url" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocation.Url(url)))))
      application.privacyPolicyUrl shouldBe Some(url)
    }
    "be correct for new journey app when location was in desktop app" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocation.InDesktopSoftware))))
      application.privacyPolicyUrl shouldBe None
    }
    "be correct for new journey app when location was not supplied" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocation.NoneProvided))))
      application.privacyPolicyUrl shouldBe None
    }
  }

  "terms and conditions location" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.copy(access = Standard(termsAndConditionsUrl = None))
      application.termsAndConditionsUrl shouldBe None
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.copy(access = Standard(termsAndConditionsUrl = Some(url)))
      application.termsAndConditionsUrl shouldBe Some(url)
    }
    "be correct for new journey app when location was url" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocation.Url(url)))))
      application.termsAndConditionsUrl shouldBe Some(url)
    }
    "be correct for new journey app when location was in desktop app" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocation.InDesktopSoftware))))
      application.termsAndConditionsUrl shouldBe None
    }
    "be correct for new journey app when location was not supplied" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocation.NoneProvided))))
      application.termsAndConditionsUrl shouldBe None
    }
  }

  "privacyPolicyInDesktopApp" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.copy(access = Standard(privacyPolicyUrl = None))
      application.privacyPolicyInDesktopApp shouldBe false
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.copy(access = Standard(privacyPolicyUrl = Some(url)))
      application.privacyPolicyInDesktopApp shouldBe false
    }
    "be correct for new journey app when location was url" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocation.Url(url)))))
      application.privacyPolicyInDesktopApp shouldBe false
    }
    "be correct for new journey app when location was in desktop app" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocation.InDesktopSoftware))))
      application.privacyPolicyInDesktopApp shouldBe true
    }
    "be correct for new journey app when location was not supplied" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocation.NoneProvided))))
      application.privacyPolicyInDesktopApp shouldBe false
    }
  }

  "termsAndConditionsInDesktopApp" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.copy(access = Standard(termsAndConditionsUrl = None))
      application.termsAndConditionsInDesktopApp shouldBe false
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.copy(access = Standard(termsAndConditionsUrl = Some(url)))
      application.termsAndConditionsInDesktopApp shouldBe false
    }
    "be correct for new journey app when location was url" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocation.Url(url)))))
      application.termsAndConditionsInDesktopApp shouldBe false
    }
    "be correct for new journey app when location was in desktop app" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocation.InDesktopSoftware))))
      application.termsAndConditionsInDesktopApp shouldBe true
    }
    "be correct for new journey app when location was not supplied" in {
      val application = baseApplication.copy(access = Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocation.NoneProvided))))
      application.termsAndConditionsInDesktopApp shouldBe false
    }
  }
}
