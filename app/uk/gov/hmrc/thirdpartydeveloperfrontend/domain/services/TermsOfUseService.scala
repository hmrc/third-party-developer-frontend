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

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, CheckInformation, Standard, TermsOfUseAcceptance, TermsOfUseAgreement}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails

import java.time.LocalDateTime
import javax.inject.Singleton

object TermsOfUseService {
  case class TermsOfUseAgreementDetails(emailAddress: String, name: Option[String], date: LocalDateTime, version: Option[String])
}

@Singleton
class TermsOfUseService {
  private def getAgreementDetailsFromCheckInformation(checkInformation: CheckInformation): List[TermsOfUseAgreementDetails] = {
    checkInformation.termsOfUseAgreements.map((toua: TermsOfUseAgreement) => TermsOfUseAgreementDetails(toua.emailAddress, None, toua.timeStamp, Some(toua.version)))
  }

  private def getAgreementDetailsFromStandardApp(std: Standard): List[TermsOfUseAgreementDetails] = {
    std.importantSubmissionData.fold[List[TermsOfUseAgreementDetails]](List.empty)(isd => isd.termsOfUseAcceptances
      .map((toua: TermsOfUseAcceptance) => TermsOfUseAgreementDetails(toua.responsibleIndividual.emailAddress.value, Some(toua.responsibleIndividual.fullName.value), toua.dateTime, None)))
  }

  def getAgreementDetails(application: Application): List[TermsOfUseAgreementDetails] =
    application.checkInformation.fold[List[TermsOfUseAgreementDetails]](List.empty)(getAgreementDetailsFromCheckInformation) ++ (
      application.access match {
        case std: Standard => getAgreementDetailsFromStandardApp(std)
        case _ => List.empty
      }
    )
}
