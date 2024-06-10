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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services

import java.time.Period

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails

class TermsOfUseServiceSpec extends HmrcSpec with FixedClock {

  def buildApplication(checkInfoAgreements: Option[List[TermsOfUseAgreement]] = None, standardAppAgreements: Option[List[TermsOfUseAcceptance]] = None): Application = Application(
    ApplicationId.random,
    ClientId("clientId"),
    "App name 1",
    instant,
    Some(instant),
    None,
    Period.ofDays(10),
    Environment.PRODUCTION,
    Some("Description 1"),
    Set.empty,
    state = ApplicationState(State.PRODUCTION, Some("user@example.com"), Some("user"), Some(""), instant),
    access = Access.Standard(importantSubmissionData =
      standardAppAgreements.map(standardAppAgreements =>
        ImportantSubmissionData(
          Some("http://example.com"),
          responsibleIndividual,
          Set.empty,
          TermsAndConditionsLocations.InDesktopSoftware,
          PrivacyPolicyLocations.InDesktopSoftware,
          standardAppAgreements
        )
      )
    ),
    checkInformation = checkInfoAgreements.map(agreements => CheckInformation(termsOfUseAgreements = agreements))
  )

  val email: LaxEmailAddress                       = "bob@example.com".toLaxEmail
  val name                                         = "Bob Example"
  val responsibleIndividual: ResponsibleIndividual = ResponsibleIndividual(FullName(name), email)
  val version1_2                                   = "1.2"
  val appWithNoAgreements: Application             = buildApplication()
  val checkInfoAgreement: TermsOfUseAgreement      = TermsOfUseAgreement(email, instant, version1_2)
  val stdAppAgreement: TermsOfUseAcceptance        = TermsOfUseAcceptance(responsibleIndividual, instant, SubmissionId.random, 0)
  val appWithCheckInfoAgreements: Application      = buildApplication(Some(List(checkInfoAgreement)))
  val appWithStdAppAgreements: Application         = buildApplication(None, Some(List(stdAppAgreement)))
  val nonStdApp: Application                       = buildApplication().copy(access = Access.Privileged())
  val underTest                                    = new TermsOfUseService()

  "getAgreementDetails" should {
    "return empty list if no agreements found" in {
      val agreements = underTest.getAgreementDetails(appWithNoAgreements)
      agreements.size shouldBe 0
    }
    "return correctly populated agreements if details found in CheckInformation" in {
      val agreements = underTest.getAgreementDetails(appWithCheckInfoAgreements)
      agreements shouldBe List(TermsOfUseAgreementDetails(email, None, instant, Some(version1_2)))
    }
    "return correctly populated agreements if details found in ImportantSubmissionData" in {
      val agreements = underTest.getAgreementDetails(appWithStdAppAgreements)
      agreements shouldBe List(TermsOfUseAgreementDetails(email, Some(name), instant, None))
    }
    "return empty list if non-standard app is checked" in {
      val agreements = underTest.getAgreementDetails(nonStdApp)
      agreements.size shouldBe 0
    }
    "return empty list if ImportantSubmissionData is missing" in {
      val agreements =
        underTest.getAgreementDetails(appWithStdAppAgreements.copy(access = appWithStdAppAgreements.access.asInstanceOf[Access.Standard].copy(importantSubmissionData = None)))
      agreements.size shouldBe 0
    }
    "return empty list if ImportantSubmissionData.termsOfUseAcceptances is empty" in {
      val importantSubmissionData = appWithStdAppAgreements.access.asInstanceOf[Access.Standard].importantSubmissionData.get
      val agreements              = underTest.getAgreementDetails(appWithStdAppAgreements.copy(access =
        appWithStdAppAgreements.access.asInstanceOf[Access.Standard].copy(importantSubmissionData = Some(importantSubmissionData.copy(termsOfUseAcceptances = List.empty)))
      ))
      agreements.size shouldBe 0
    }
  }
}
