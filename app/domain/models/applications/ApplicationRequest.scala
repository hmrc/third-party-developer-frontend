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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import play.api.libs.json.Json
import uk.gov.hmrc.modules.uplift.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier

case class UpliftData(
  responsibleIndividual: ResponsibleIndividual,
  sellResellOrDistribute: SellResellOrDistribute,
  subscriptions: Set[ApiIdentifier]
)

object UpliftData {
  import play.api.libs.json.{Format, Json}
  import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.ApiDefinitionsJsonFormatters._
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
    access: Access = Standard(List.empty, None, None, Set.empty)
)

object CreateApplicationRequest extends ApplicationRequest {
  implicit val format = Json.format[CreateApplicationRequest]

  def fromAddApplicationJourney(user: DeveloperSession, form: AddApplicationNameForm, environment: Environment) = CreateApplicationRequest(
    name = form.applicationName.trim,
    environment = environment,
    description = None,
    collaborators = List(Collaborator(user.email, CollaboratorRole.ADMINISTRATOR, user.developer.userId))
  )
}

case class UpdateApplicationRequest(
    id: ApplicationId,
    environment: Environment,
    name: String,
    description: Option[String] = None,
    access: Access = Standard(List.empty, None, None, Set.empty)
)

object UpdateApplicationRequest extends ApplicationRequest {

  implicit val format = Json.format[UpdateApplicationRequest]

  def from(form: EditApplicationForm, application: Application) = {
    val name = if (application.state.name == State.TESTING || application.deployedTo.isSandbox) {
      form.applicationName.trim
    } else {
      application.name
    }

    val access = application.access.asInstanceOf[Standard]

    UpdateApplicationRequest(
      application.id,
      application.deployedTo,
      name,
      normalizeDescription(form.description),
      Standard(
        access.redirectUris.map(_.trim).filter(_.nonEmpty).distinct,
        form.termsAndConditionsUrl.map(_.trim),
        form.privacyPolicyUrl.map(_.trim)
      )
    )
  }

  def from(application: Application, form: AddRedirectForm): UpdateApplicationRequest = {
    val access = application.access.asInstanceOf[Standard]

    UpdateApplicationRequest(
      application.id,
      application.deployedTo,
      application.name,
      normalizeDescription(application.description),
      access.copy(redirectUris = (access.redirectUris ++ List(form.redirectUri)).distinct)
    )
  }

  def from(application: Application, form: DeleteRedirectConfirmationForm): UpdateApplicationRequest = {
    val access = application.access.asInstanceOf[Standard]

    UpdateApplicationRequest(
      application.id,
      application.deployedTo,
      application.name,
      normalizeDescription(application.description),
      access.copy(redirectUris = access.redirectUris.filter(uri => uri != form.redirectUri))
    )
  }

  def from(application: Application, form: ChangeRedirectForm): UpdateApplicationRequest = {
    val access = application.access.asInstanceOf[Standard]

    UpdateApplicationRequest(
      application.id,
      application.deployedTo,
      application.name,
      normalizeDescription(application.description),
      access.copy(redirectUris = access.redirectUris.map {
        case form.originalRedirectUri => form.newRedirectUri
        case s                        => s
      })
    )
  }
}
