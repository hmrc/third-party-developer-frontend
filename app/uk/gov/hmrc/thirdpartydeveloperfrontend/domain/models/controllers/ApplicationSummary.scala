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

import uk.gov.hmrc.http.NotFoundException

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

case class ApplicationSummary(
    id: ApplicationId,
    name: ApplicationName,
    role: Collaborator.Role,
    termsOfUseStatus: TermsOfUseStatus,
    state: State,
    lastAccess: Option[Instant],
    grantLength: GrantLength,
    serverTokenUsed: Boolean = false,
    createdOn: Instant,
    accessType: AccessType,
    environment: Environment,
    subscriptionIds: Set[ApiIdentifier]
  )

object ApplicationSummary extends ApplicationSyntaxes {

  def from(app: ApplicationWithCollaborators, userId: UserId): ApplicationSummary = {

    val role = app.roleFor(userId).getOrElse(throw new NotFoundException("Role not found"))

    ApplicationSummary(
      app.id,
      app.details.name,
      role,
      app.termsOfUseStatus,
      app.state.name,
      app.details.lastAccess,
      app.details.grantLength,
      app.details.token.lastAccessTokenUsage.isDefined,
      app.details.createdOn,
      app.access.accessType,
      app.deployedTo,
      Set.empty
    )
  }

  def from(app: ApplicationWithSubscriptions, userId: UserId): ApplicationSummary = {
    val role = app.roleFor(userId).getOrElse(throw new NotFoundException("Role not found"))

    ApplicationSummary(
      app.id,
      app.name,
      role,
      app.termsOfUseStatus,
      app.state.name,
      app.details.lastAccess,
      app.details.grantLength,
      app.details.token.lastAccessTokenUsage.isDefined,
      app.details.createdOn,
      app.access.accessType,
      app.deployedTo,
      app.subscriptions
    )
  }

  implicit val createdOnOrdering: Ordering[ApplicationSummary] = Ordering.by[ApplicationSummary, Instant](_.createdOn).reverse
}
