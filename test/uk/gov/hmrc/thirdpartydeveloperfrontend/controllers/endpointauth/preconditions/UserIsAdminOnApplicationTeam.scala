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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.MockConnectors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APIAccessType.PUBLIC
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APIStatus.STABLE
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{APIAccess, APIStatus, ApiContext, ApiIdentifier, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ApplicationState, ApplicationToken, ApplicationWithSubscriptionData, ApplicationWithSubscriptionIds, ClientId, ClientSecret, ClientSecretRequest, Collaborator, CollaboratorRole, Environment, ImportantSubmissionData, IpAllowlist, PrivacyPolicyLocation, ResponsibleIndividual, Standard, TermsAndConditionsLocation}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.{SaveSubscriptionFieldsSuccessResponse, SubscriptionFieldDefinition}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{AccessRequirements, ApiCategory, ApiData, FieldName, FieldValue, Fields, VersionData}

import java.time.{LocalDateTime, Period}
import scala.concurrent.Future

trait UserIsAdminOnApplicationTeam extends MockConnectors with UserIsAuthenticated with HasApplicationId {
  this: ApplicationHasState =>

  val access = Standard(importantSubmissionData = Some(
    ImportantSubmissionData(None, ResponsibleIndividual.build("ri name", "ri@example.com"), Set.empty, TermsAndConditionsLocation.InDesktopSoftware, PrivacyPolicyLocation.InDesktopSoftware, List.empty)))
  val collaborators = Set(Collaborator(user.email, CollaboratorRole.ADMINISTRATOR, user.userId))
  val application = Application(
    applicationId, ClientId.random, "my app", LocalDateTime.of(2020, 1, 1, 0, 0, 0), None, None, Period.ofYears(1), Environment.PRODUCTION, None, collaborators, access,
    applicationState, None, IpAllowlist(false, Set.empty)
  )
  val apiContext = ApiContext("ctx")
  val apiVersion = ApiVersion("1.0")
  val apiFieldName = FieldName("my_field")
  val apiFieldValue = FieldValue("my value")
  val apiPpnsFieldName = FieldName("my_ppns_field")
  val apiPpnsFieldValue = FieldValue("my ppns value")
  val appWithSubsIds = ApplicationWithSubscriptionIds.from(application)
  val appWithSubsData = ApplicationWithSubscriptionData(application, Set(ApiIdentifier(apiContext, apiVersion)), Map(
    apiContext -> Map(ApiVersion("1.0") -> Map(apiFieldName -> apiFieldValue, apiPpnsFieldName -> apiPpnsFieldValue))
  ))

  when(tpaProductionConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
  when(tpaSandboxConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
  when(apmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(Future.successful(Some(appWithSubsData)))
  when(apmConnector.getAllFieldDefinitions(*[Environment])(*)).thenReturn(Future.successful(Map(
    apiContext -> Map(apiVersion -> Map(
      apiFieldName -> SubscriptionFieldDefinition(apiFieldName, "field desc", "field short desc", "hint", "STRING", AccessRequirements.Default),
      apiPpnsFieldName -> SubscriptionFieldDefinition(apiPpnsFieldName, "field desc", "field short desc", "hint", "PPNSField", AccessRequirements.Default)
    )
  ))))
  when(apmConnector.fetchAllOpenAccessApis(*[Environment])(*)).thenReturn(Future.successful(Map.empty))
  when(apmConnector.fetchAllPossibleSubscriptions(*[ApplicationId])(*)).thenReturn(Future.successful(Map(
    apiContext -> ApiData("service name", "api name", true, Map(apiVersion -> VersionData(STABLE, APIAccess(PUBLIC))), List(ApiCategory("category")))
  )))
  when(tpaProductionConnector.fetchCredentials(*[ApplicationId])(*)).thenReturn(Future.successful(ApplicationToken(List(ClientSecret("s1id", "s1name", LocalDateTime.now(), None)), "secret")))
  when(tpaProductionConnector.deleteClientSecret(*[ApplicationId], *, *)(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.addClientSecrets(*[ApplicationId], *[ClientSecretRequest])(*)).thenReturn(Future.successful(("1","2")))
  when(productionSubsFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *[Fields.Alias])(*)).thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))
  when(productionPushPullNotificationsConnector.fetchPushSecrets(*[ClientId])(*)).thenReturn(Future.successful(List("secret1")))
}
