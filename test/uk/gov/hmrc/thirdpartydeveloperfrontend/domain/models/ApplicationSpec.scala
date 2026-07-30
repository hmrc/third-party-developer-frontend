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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{ResponsibleIndividual, TermsOfUseAcceptance, *}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.*
import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}

class ApplicationSpec extends HmrcSpec with FixedClock with ApplicationWithCollaboratorsFixtures {
  val url = "http://example.com"

  val importantSubmissionData = ImportantSubmissionData(
    None,
    ResponsibleIndividual(FullName("bob"), LaxEmailAddress("bob")),
    Set.empty[ServerLocation],
    TermsAndConditionsLocation.NoneProvided,
    PrivacyPolicyLocation.NoneProvided,
    List.empty[TermsOfUseAcceptance]
  )

  val baseApplication = standardApp

  "privacy policy location" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.withAccess(Access.Standard(privacyPolicyUrl = None))
      application.privacyPolicyLocation shouldBe Some(PrivacyPolicyLocation.NoneProvided)
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.withAccess(Access.Standard(privacyPolicyUrl = Some(url)))
      application.privacyPolicyLocation shouldBe Some(PrivacyPolicyLocation.Url(url))
    }
    "be correct for new journey app when location was url" in {
      val application =
        baseApplication.withAccess(Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocation.Url(url)))))
      application.privacyPolicyLocation shouldBe Some(PrivacyPolicyLocation.Url(url))
    }
    "be correct for new journey app when location was in desktop app" in {
      val application =
        baseApplication.withAccess(Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocation.InDesktopSoftware))))
      application.privacyPolicyLocation shouldBe Some(PrivacyPolicyLocation.InDesktopSoftware)
    }
    "be correct for new journey app when location was not supplied" in {
      val application =
        baseApplication.withAccess(Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocation.NoneProvided))))
      application.privacyPolicyLocation shouldBe Some(PrivacyPolicyLocation.NoneProvided)
    }
  }

  "terms and conditions location" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.withAccess(Access.Standard(termsAndConditionsUrl = None))
      application.termsAndConditionsLocation shouldBe Some(TermsAndConditionsLocation.NoneProvided)
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.withAccess(Access.Standard(termsAndConditionsUrl = Some(url)))
      application.termsAndConditionsLocation shouldBe Some(TermsAndConditionsLocation.Url(url))
    }
    "be correct for new journey app when location was url" in {
      val application =
        baseApplication.withAccess(Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocation.Url(url)))))
      application.termsAndConditionsLocation shouldBe Some(TermsAndConditionsLocation.Url(url))
    }
    "be correct for new journey app when location was in desktop app" in {
      val application =
        baseApplication.withAccess(
          Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocation.InDesktopSoftware)))
        )
      application.termsAndConditionsLocation shouldBe Some(TermsAndConditionsLocation.InDesktopSoftware)
    }
    "be correct for new journey app when location was not supplied" in {
      val application =
        baseApplication.withAccess(
          Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocation.NoneProvided)))
        )
      application.termsAndConditionsLocation shouldBe Some(TermsAndConditionsLocation.NoneProvided)
    }
  }
}
