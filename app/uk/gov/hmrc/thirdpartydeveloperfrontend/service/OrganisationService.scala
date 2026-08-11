/*
 * Copyright 2026 HM Revenue & Customs
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

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.*
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{OrganisationAllowList, Submission}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.*

@Singleton
class OrganisationService @Inject() (
    organisationConnector: OrganisationConnector,
    val clock: Clock
  )(using val ec: ExecutionContext
  ) extends ClockNow {

  def fetchOrganisationAllowList(userId: UserId)(using HeaderCarrier): Future[Option[OrganisationAllowList]] = {
    organisationConnector.fetchOrganisationAllowList(userId)
  }

  def fetchLatestSubmissionByUserId(userId: UserId)(using HeaderCarrier): Future[Option[Submission]] = {
    organisationConnector.fetchLatestSubmissionByUserId(userId)
  }
}
