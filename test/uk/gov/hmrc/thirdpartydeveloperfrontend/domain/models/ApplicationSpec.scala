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

import java.time.Period

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{ResponsibleIndividual, TermsOfUseAcceptance, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

class ApplicationSpec extends HmrcSpec with FixedClock {
  val url            = "http://example.com"
  val standardAccess = Access.Standard()

  val importantSubmissionData = ImportantSubmissionData(
    None,
    ResponsibleIndividual(FullName("bob"), LaxEmailAddress("bob")),
    Set.empty[ServerLocation],
    TermsAndConditionsLocations.NoneProvided,
    PrivacyPolicyLocations.NoneProvided,
    List.empty[TermsOfUseAcceptance]
  )

  val baseApplication = Application(
    ApplicationId.random,
    ClientId("client"),
    "name",
    instant,
    Some(instant),
    None,
    Period.ofDays(1),
    Environment.PRODUCTION,
    access = standardAccess
  )

  "privacy policy location" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.copy(access = Access.Standard(privacyPolicyUrl = None))
      application.privacyPolicyLocation shouldBe PrivacyPolicyLocations.NoneProvided
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.copy(access = Access.Standard(privacyPolicyUrl = Some(url)))
      application.privacyPolicyLocation shouldBe PrivacyPolicyLocations.Url(url)
    }
    "be correct for new journey app when location was url" in {
      val application =
        baseApplication.copy(access = Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocations.Url(url)))))
      application.privacyPolicyLocation shouldBe PrivacyPolicyLocations.Url(url)
    }
    "be correct for new journey app when location was in desktop app" in {
      val application =
        baseApplication.copy(access = Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocations.InDesktopSoftware))))
      application.privacyPolicyLocation shouldBe PrivacyPolicyLocations.InDesktopSoftware
    }
    "be correct for new journey app when location was not supplied" in {
      val application =
        baseApplication.copy(access = Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(privacyPolicyLocation = PrivacyPolicyLocations.NoneProvided))))
      application.privacyPolicyLocation shouldBe PrivacyPolicyLocations.NoneProvided
    }
  }

  "terms and conditions location" should {
    "be correct for old journey app when no location supplied" in {
      val application = baseApplication.copy(access = Access.Standard(termsAndConditionsUrl = None))
      application.termsAndConditionsLocation shouldBe TermsAndConditionsLocations.NoneProvided
    }
    "be correct for old journey app when location was supplied" in {
      val application = baseApplication.copy(access = Access.Standard(termsAndConditionsUrl = Some(url)))
      application.termsAndConditionsLocation shouldBe TermsAndConditionsLocations.Url(url)
    }
    "be correct for new journey app when location was url" in {
      val application =
        baseApplication.copy(access = Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocations.Url(url)))))
      application.termsAndConditionsLocation shouldBe TermsAndConditionsLocations.Url(url)
    }
    "be correct for new journey app when location was in desktop app" in {
      val application =
        baseApplication.copy(access =
          Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocations.InDesktopSoftware)))
        )
      application.termsAndConditionsLocation shouldBe TermsAndConditionsLocations.InDesktopSoftware
    }
    "be correct for new journey app when location was not supplied" in {
      val application =
        baseApplication.copy(access =
          Access.Standard(importantSubmissionData = Some(importantSubmissionData.copy(termsAndConditionsLocation = TermsAndConditionsLocations.NoneProvided)))
        )
      application.termsAndConditionsLocation shouldBe TermsAndConditionsLocations.NoneProvided
    }
  }
}
