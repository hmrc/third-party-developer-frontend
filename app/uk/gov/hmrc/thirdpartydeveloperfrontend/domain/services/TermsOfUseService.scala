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

import java.time.Instant
import javax.inject.Singleton

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, CheckInformation, TermsOfUseAgreement}
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.TermsOfUseAcceptance
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails

object TermsOfUseService {
  case class TermsOfUseAgreementDetails(emailAddress: LaxEmailAddress, name: Option[String], date: Instant, version: Option[String])
}

@Singleton
class TermsOfUseService {

  private def getAgreementDetailsFromCheckInformation(checkInformation: CheckInformation): List[TermsOfUseAgreementDetails] = {
    checkInformation.termsOfUseAgreements.map((toua: TermsOfUseAgreement) => TermsOfUseAgreementDetails(toua.emailAddress, None, toua.timeStamp, Some(toua.version)))
  }

  private def getAgreementDetailsFromStandardApp(std: Access.Standard): List[TermsOfUseAgreementDetails] = {
    std.importantSubmissionData.fold[List[TermsOfUseAgreementDetails]](List.empty)(isd =>
      isd.termsOfUseAcceptances
        .map((toua: TermsOfUseAcceptance) =>
          TermsOfUseAgreementDetails(toua.responsibleIndividual.emailAddress, Some(toua.responsibleIndividual.fullName.value), toua.dateTime, None)
        )
    )
  }

  def getAgreementDetails(application: ApplicationWithCollaborators): List[TermsOfUseAgreementDetails] =
    application.details.checkInformation.fold[List[TermsOfUseAgreementDetails]](List.empty)(getAgreementDetailsFromCheckInformation) ++ (
      application.access match {
        case std: Access.Standard => getAgreementDetailsFromStandardApp(std)
        case _                    => List.empty
      }
    )
}
