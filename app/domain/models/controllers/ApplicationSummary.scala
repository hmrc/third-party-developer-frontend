/*
 * Copyright 2021 HM Revenue & Customs
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

package domain.models.controllers

import domain.models.applications._
import domain.models.apidefinitions.AccessType
import uk.gov.hmrc.http.NotFoundException
import org.joda.time.DateTime

trait ApplicationSummary {
  def id: ApplicationId
  def name: String
  def environment: Environment
  def role: CollaboratorRole
  def termsOfUseStatus: TermsOfUseStatus
  def state: State
  def lastAccess: DateTime
  def serverTokenUsed: Boolean
  def createdOn: DateTime
  def accessType: AccessType
}

case class ProductionApplicationSummary(
  id: ApplicationId,
  name: String,
  role: CollaboratorRole,
  termsOfUseStatus: TermsOfUseStatus,
  state: State,
  lastAccess: DateTime,
  serverTokenUsed: Boolean = false,
  createdOn: DateTime,
  accessType: AccessType
) extends ApplicationSummary {
  val environment = Environment.PRODUCTION
}

case class SandboxApplicationSummary(
  id: ApplicationId,
  name: String,
  role: CollaboratorRole,
  termsOfUseStatus: TermsOfUseStatus,
  state: State,
  lastAccess: DateTime,
  serverTokenUsed: Boolean = false,
  createdOn: DateTime,
  accessType: AccessType,
  isValidTargetForUplift: Boolean
) extends ApplicationSummary {
  val environment = Environment.SANDBOX
}

object SandboxApplicationSummary {
  def from(app: Application, email: String): SandboxApplicationSummary = {
    require(app.deployedTo.isSandbox, "SandboxApplicationSummary cannot be built from Production App")

    val role = app.role(email).getOrElse(throw new NotFoundException("Role not found"))
    val isValidTargetForUplift = false

    SandboxApplicationSummary(
      app.id,
      app.name,
      role,
      app.termsOfUseStatus,
      app.state.name,
      app.lastAccess,
      app.lastAccessTokenUsage.isDefined,
      app.createdOn,
      app.access.accessType,
      isValidTargetForUplift
    )
  }
}

object ProductionApplicationSummary {
  def from(app: Application, email: String): ProductionApplicationSummary = {
    require(app.deployedTo.isProduction, "ProductionApplicationSummary cannot be built from Sandbox App")

    val role = app.role(email).getOrElse(throw new NotFoundException("Role not found"))

    ProductionApplicationSummary(
      app.id,
      app.name,
      role,
      app.termsOfUseStatus,
      app.state.name,
      app.lastAccess,
      app.lastAccessTokenUsage.isDefined,
      app.createdOn,
      app.access.accessType        
    )
  }
}
