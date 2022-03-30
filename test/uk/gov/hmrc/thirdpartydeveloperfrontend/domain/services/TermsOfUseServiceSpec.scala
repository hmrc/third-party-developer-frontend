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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services

import org.joda.time.DateTime
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ApplicationState, CheckInformation, ClientId, Environment, ImportantSubmissionData, PrivacyPolicyLocation, Privileged, ResponsibleIndividual, Standard, TermsAndConditionsLocation, TermsOfUseAcceptance, TermsOfUseAgreement}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.HmrcSpec
import uk.gov.hmrc.time.DateTimeUtils

import java.time.Period

class TermsOfUseServiceSpec extends HmrcSpec {
  def buildApplication(checkInfoAgreements: Option[List[TermsOfUseAgreement]] = None, standardAppAgreements: Option[List[TermsOfUseAcceptance]] = None) = Application(
    ApplicationId.random,
    ClientId("clientId"),
    "App name 1",
    DateTimeUtils.now,
    DateTimeUtils.now,
    None,
    Period.ofDays(10),
    Environment.PRODUCTION,
    Some("Description 1"),
    Set.empty,
    state = ApplicationState.production("user@example.com", ""),
    access = Standard(importantSubmissionData = standardAppAgreements.map(standardAppAgreements => ImportantSubmissionData(
      Some("http://example.com"),
      responsibleIndividual,
      Set.empty,
      TermsAndConditionsLocation.InDesktopSoftware,
      PrivacyPolicyLocation.InDesktopSoftware,
      standardAppAgreements
    ))),
    checkInformation = checkInfoAgreements.map(agreements => CheckInformation(termsOfUseAgreements = agreements))
  )

  val timestamp = DateTime.now
  val email = "bob@example.com"
  val name = "Bob Example"
  val responsibleIndividual = ResponsibleIndividual(ResponsibleIndividual.Name(name), ResponsibleIndividual.EmailAddress(email))
  val version1_2 = "1.2"
  val appWithNoAgreements = buildApplication()
  val checkInfoAgreement = TermsOfUseAgreement(email, timestamp, version1_2)
  val stdAppAgreement = TermsOfUseAcceptance(responsibleIndividual, timestamp, Submission.Id.random)
  val appWithCheckInfoAgreements = buildApplication(Some(List(checkInfoAgreement)))
  val appWithStdAppAgreements = buildApplication(None, Some(List(stdAppAgreement)))
  val nonStdApp = buildApplication().copy(access = Privileged())
  val underTest = new TermsOfUseService()

  "getAgreementDetails" should {
    "return empty list if no agreements found" in {
      val agreements = underTest.getAgreementDetails(appWithNoAgreements)
      agreements.size shouldBe 0
    }
    "return correctly populated agreements if details found in CheckInformation" in {
      val agreements = underTest.getAgreementDetails(appWithCheckInfoAgreements)
      agreements shouldBe List(TermsOfUseAgreementDetails(email, None, timestamp, Some(version1_2)))
    }
    "return correctly populated agreements if details found in ImportantSubmissionData" in {
      val agreements = underTest.getAgreementDetails(appWithStdAppAgreements)
      agreements shouldBe List(TermsOfUseAgreementDetails(email, Some(name), timestamp, None))
    }
    "return empty list if non-standard app is checked" in {
      val agreements = underTest.getAgreementDetails(nonStdApp)
      agreements.size shouldBe 0
    }
    "return empty list if ImportantSubmissionData is missing" in {
      val agreements = underTest.getAgreementDetails(appWithStdAppAgreements.copy(access = appWithStdAppAgreements.access.asInstanceOf[Standard].copy(importantSubmissionData = None)))
      agreements.size shouldBe 0
    }
    "return empty list if ImportantSubmissionData.termsOfUseAcceptances is empty" in {
      val importantSubmissionData = appWithStdAppAgreements.access.asInstanceOf[Standard].importantSubmissionData.get
      val agreements = underTest.getAgreementDetails(appWithStdAppAgreements.copy(access = appWithStdAppAgreements.access.asInstanceOf[Standard].copy(importantSubmissionData = Some(importantSubmissionData.copy(termsOfUseAcceptances = List.empty)))))
      agreements.size shouldBe 0
    }
  }
}
