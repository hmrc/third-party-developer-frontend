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

import java.time.{LocalDateTime, Period}

import uk.gov.hmrc.http.NotFoundException

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{AccessType, ApiIdentifier}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.apiplatform.modules.developers.domain.models.UserId

case class ApplicationSummary(
    id: ApplicationId,
    name: String,
    role: CollaboratorRole,
    termsOfUseStatus: TermsOfUseStatus,
    state: State,
    lastAccess: Option[LocalDateTime],
    grantLength: Period,
    serverTokenUsed: Boolean = false,
    createdOn: LocalDateTime,
    accessType: AccessType,
    environment: Environment,
    subscriptionIds: Set[ApiIdentifier]
  )

object ApplicationSummary {

  def from(app: Application, userId: UserId): ApplicationSummary = {

    val role = app.roleForCollaborator(userId).getOrElse(throw new NotFoundException("Role not found"))

    ApplicationSummary(
      app.id,
      app.name,
      role,
      app.termsOfUseStatus,
      app.state.name,
      app.lastAccess,
      app.grantLength,
      app.lastAccessTokenUsage.isDefined,
      app.createdOn,
      app.access.accessType,
      app.deployedTo,
      Set.empty
    )
  }

  def from(app: ApplicationWithSubscriptionIds, userId: UserId): ApplicationSummary = {

    val role = app.roleForCollaborator(userId).getOrElse(throw new NotFoundException("Role not found"))

    ApplicationSummary(
      app.id,
      app.name,
      role,
      app.termsOfUseStatus,
      app.state.name,
      app.lastAccess,
      app.grantLength,
      app.lastAccessTokenUsage.isDefined,
      app.createdOn,
      app.access.accessType,
      app.deployedTo,
      app.subscriptions
    )
  }
}
