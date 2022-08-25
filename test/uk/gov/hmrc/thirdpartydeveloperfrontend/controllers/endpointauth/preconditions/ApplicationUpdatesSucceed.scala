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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions

import play.api.http.Status.OK
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.MockConnectors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationNameValidationJson.ApplicationNameValidationResult
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, ApplicationNameValidation, ApplicationUpdate, CheckInformation, ClientId, ClientSecretRequest, UpdateApplicationRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.AddTeamMemberRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SaveSubscriptionFieldsSuccessResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.Fields

import scala.concurrent.Future

trait ApplicationUpdateSucceeds extends MockConnectors {
  when(tpaProductionConnector.applicationUpdate(*[ApplicationId],*[ApplicationUpdate])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.updateApproval(*[ApplicationId],*[CheckInformation])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.updateApproval(*[ApplicationId],*[CheckInformation])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.update(*[ApplicationId],*[UpdateApplicationRequest])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.update(*[ApplicationId],*[UpdateApplicationRequest])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.updateIpAllowlist(*[ApplicationId],*[Boolean], *[Set[String]])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.addClientSecrets(*[ApplicationId], *[ClientSecretRequest])(*)).thenReturn(Future.successful(("1","2")))
  when(tpaProductionConnector.deleteClientSecret(*[ApplicationId], *, *)(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(apmConnector.addTeamMember(*[ApplicationId],*[AddTeamMemberRequest])(*)).thenReturn(Future.successful(OK))
  when(apmConnector.subscribeToApi(*[ApplicationId],*[ApiIdentifier])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(productionSubsFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *[Fields.Alias])(*)).thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))
  when(sandboxSubsFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *[Fields.Alias])(*)).thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))
  when(tpaSandboxConnector.validateName(*[String],*[Option[ApplicationId]])(*)).thenReturn(Future.successful(ApplicationNameValidation(ApplicationNameValidationResult(None))))
}
