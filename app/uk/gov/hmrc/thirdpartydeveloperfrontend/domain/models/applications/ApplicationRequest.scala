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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession

case class UpliftData(
    sellResellOrDistribute: SellResellOrDistribute,
    subscriptions: Set[ApiIdentifier],
    requestedBy: LaxEmailAddress
  )

object UpliftData {
  import play.api.libs.json.{Format, Json}
  implicit val format: Format[UpliftData] = Json.format[UpliftData]
}

trait ApplicationRequest {
  protected def normalizeDescription(description: Option[String]): Option[String] = description.map(_.trim.take(250))
}

case class CreateApplicationRequest(
    name: String,
    environment: Environment,
    description: Option[String],
    collaborators: List[Collaborator],
    access: Access = Access.Standard(List.empty, None, None, Set.empty)
  )

object CreateApplicationRequest extends ApplicationRequest {
  import play.api.libs.json.{Json, OFormat}
  implicit val format: OFormat[CreateApplicationRequest] = Json.format[CreateApplicationRequest]

  def fromAddApplicationJourney(user: DeveloperSession, form: AddApplicationNameForm, environment: Environment) = CreateApplicationRequest(
    name = form.applicationName.trim,
    environment = environment,
    description = None,
    collaborators = List(Collaborator(user.email, Collaborator.Roles.ADMINISTRATOR, user.developer.userId))
  )
}
