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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers

import java.time.Instant

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitation

case class ManageApplicationsViewModel(
    sandboxApplicationSummaries: Seq[ApplicationSummary],
    productionApplicationSummaries: Seq[ApplicationSummary],
    upliftableApplicationIds: Set[ApplicationId],
    hasAppsThatCannotBeUplifted: Boolean,
    termsOfUseInvitations: List[TermsOfUseInvitation],
    productionApplicationSubmissions: List[Submission]
  ) {

  lazy val hasPriviledgedApplications =
    sandboxApplicationSummaries.exists(_.accessType == AccessType.PRIVILEGED) || productionApplicationSummaries.exists(_.accessType == AccessType.PRIVILEGED)
  lazy val hasAppsThatCanBeUplifted   = upliftableApplicationIds.nonEmpty

  lazy val notYetLiveProductionApplications = productionApplicationSummaries.filterNot(_.state.isApproved)
  lazy val liveProductionApplications       = productionApplicationSummaries.filter(_.state.isApproved)

  lazy val hasLiveProductionApplicationsInvitedToUpgradeToNewTermsOfUse = false

  lazy val hasNoLiveProductionApplications = liveProductionApplications.isEmpty

  lazy val applicationsThatHaveTermOfUseInvitatationsOutstanding =
    liveProductionApplications
      .filter(app => app.role == Collaborator.Roles.ADMINISTRATOR)
      .filter(app => termsOfUseInvitations.exists(app.id == _.applicationId))
      .filter(app =>
        !productionApplicationSubmissions.exists(sub => app.id == sub.applicationId) ||
          productionApplicationSubmissions.exists(sub => app.id == sub.applicationId && sub.status.isOpenToAnswers)
      )
      .map(applicationSummary =>
        TermsOfUseInvitationViewModel(applicationSummary.id, applicationSummary.name, termsOfUseInvitations.find(_.applicationId == applicationSummary.id).get.dueBy)
      )

  lazy val applicationsThatHaveTermOfUseInvitatationsSubmitted =
    liveProductionApplications
      .filter(app => app.role == Collaborator.Roles.ADMINISTRATOR)
      .filter(app => termsOfUseInvitations.exists(app.id == _.applicationId))
      .filter(app =>
        productionApplicationSubmissions.exists(sub =>
          app.id == sub.applicationId && (sub.status.isFailed || sub.status.isWarnings || sub.status.isPendingResponsibleIndividual || sub.status.isSubmitted)
        )
      )
      .map(applicationSummary =>
        TermsOfUseInvitationViewModel(applicationSummary.id, applicationSummary.name, termsOfUseInvitations.find(_.applicationId == applicationSummary.id).get.dueBy)
      )

}

case class TermsOfUseInvitationViewModel(applicationId: ApplicationId, name: ApplicationName, dueBy: Instant)
