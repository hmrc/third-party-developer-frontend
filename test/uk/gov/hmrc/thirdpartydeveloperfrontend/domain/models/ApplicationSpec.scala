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
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{ResponsibleIndividual, TermsOfUseAcceptance, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}

class ApplicationSpec extends HmrcSpec with FixedClock with ApplicationWithCollaboratorsFixtures {
  val url = "http://example.com"

  val importantSubmissionData = ImportantSubmissionData(
    None,
    ResponsibleIndividual(FullName("bob"), LaxEmailAddress("bob")),
    Set.empty[ServerLocation],
    TermsAndConditionsLocations.NoneProvided,
    PrivacyPolicyLocations.NoneProvided,
    List.empty[TermsOfUseAcceptance]
  )

  val baseApplication = standardApp

  "privacy policy location" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.withAccess(Access.Standard(privacyPolicyUrl = None))
      application.privacyPolicyLocation shouldBe None
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.withAccess(Access.Standard(privacyPolicyUrl = Some(url)))
      application.privacyPolicyLocation shouldBe Some(PrivacyPolicyLocations.Url(url))
    }
    "be correct for new journey app when location was url" in {
      val application =
        baseApplication.withAccess(Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocations.Url(url)))))
      application.privacyPolicyLocation shouldBe Some(PrivacyPolicyLocations.Url(url))
    }
    "be correct for new journey app when location was in desktop app" in {
      val application =
        baseApplication.withAccess(Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocations.InDesktopSoftware))))
      application.privacyPolicyLocation shouldBe Some(PrivacyPolicyLocations.InDesktopSoftware)
    }
    "be correct for new journey app when location was not supplied" in {
      val application =
        baseApplication.withAccess(Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocations.NoneProvided))))
      application.privacyPolicyLocation shouldBe Some(PrivacyPolicyLocations.NoneProvided)
    }
  }

  "terms and conditions location" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.withAccess(Access.Standard(termsAndConditionsUrl = None))
      application.termsAndConditionsLocation shouldBe None
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.withAccess(Access.Standard(termsAndConditionsUrl = Some(url)))
      application.termsAndConditionsLocation shouldBe Some(TermsAndConditionsLocations.Url(url))
    }
    "be correct for new journey app when location was url" in {
      val application =
        baseApplication.withAccess(Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocations.Url(url)))))
      application.termsAndConditionsLocation shouldBe Some(TermsAndConditionsLocations.Url(url))
    }
    "be correct for new journey app when location was in desktop app" in {
      val application =
        baseApplication.withAccess(
          Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocations.InDesktopSoftware)))
        )
      application.termsAndConditionsLocation shouldBe Some(TermsAndConditionsLocations.InDesktopSoftware)
    }
    "be correct for new journey app when location was not supplied" in {
      val application =
        baseApplication.withAccess(
          Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocations.NoneProvided)))
        )
      application.termsAndConditionsLocation shouldBe Some(TermsAndConditionsLocations.NoneProvided)
    }
  }
}
