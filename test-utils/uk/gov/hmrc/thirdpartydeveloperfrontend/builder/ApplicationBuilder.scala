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

package uk.gov.hmrc.thirdpartydeveloperfrontend.builder

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, _}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.UserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithSubscriptionFields
import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.ApiFieldMap
import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.FieldValue
import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.FieldName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationName
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.RedirectUri

trait ApplicationBuilder extends CollaboratorTracker with FixedClock with ApplicationStateHelper with ApplicationWithCollaboratorsFixtures {
  self: UserIdTracker =>

  def buildApplication(appOwnerEmail: LaxEmailAddress): ApplicationWithCollaborators = {

    val appId        = ApplicationId.random
    // val clientId     = ClientId("clientid-" + randomUUID.toString)
    val appOwnerName = "App owner name"
    val access = Access.Standard(
        redirectUris = List(RedirectUri.unsafeApply("https://red1"), RedirectUri.unsafeApply("https://red2")),
        termsAndConditionsUrl = Some("http://tnc-url.com")
      )

    standardApp
    .withId(appId)
    .withCollaborators(appOwnerEmail.asAdministratorCollaborator)
    .withEnvironment(Environment.SANDBOX)
    .withState(InState.production(appOwnerEmail.text, appOwnerName, ""))
    .withAccess(access)
    .modify(_.copy(name = ApplicationName(s"${appId.toString()}-name")))
    // Application(
    //   appId,
    //   clientId,
    //   s"${appId.toString()}-name",
    //   instant,
    //   Some(instant),
    //   None,
    //   grantLength = Period.ofDays(547),
    //   Environment.SANDBOX,
    //   Some(s"$appId-description"),
    //   buildCollaborators(Seq(appOwnerEmail)),
    //   state = InState.production(appOwnerEmail.text, appOwnerName, ""),
    //   access = Access.Standard(
    //     redirectUris = List(RedirectUri.unsafeApply("https://red1"), RedirectUri.unsafeApply("https://red2")),
    //     termsAndConditionsUrl = Some("http://tnc-url.com")
    //   )
    // )
  }

  def buildCollaborators(emails: Seq[LaxEmailAddress]): Set[Collaborator] = {
    emails.map(email => email.asAdministratorCollaborator).toSet
  }

  def buildApplicationWithSubscriptionFields(appOwnerEmail: LaxEmailAddress): ApplicationWithSubscriptionFields = {
    val application = buildApplication(appOwnerEmail)

    application.withSubscriptions(Set.empty).withFieldValues(Map.empty)
  }

  def buildSubscriptions(apiContext: ApiContext, apiVersion: ApiVersionNbr): Set[ApiIdentifier] =
    Set(
      ApiIdentifier(apiContext, apiVersion)
    )

  def buildSubscriptionFieldValues(apiContext: ApiContext, apiVersion: ApiVersionNbr, fields: Map[FieldName, FieldValue]): ApiFieldMap[FieldValue] = {
    Map(apiContext -> Map(apiVersion -> fields))
  }

  def buildApplicationWithSubscriptionFields(
      apiContext: ApiContext = ApiContext.random,
      apiVersion: ApiVersionNbr = ApiVersionNbr.random,
      fields: Map[FieldName, FieldValue] = Map(FieldName.random -> FieldValue.random, FieldName.random -> FieldValue.random)
    ): ApplicationWithSubscriptionFields = {
    buildApplication("email@example.com".toLaxEmail)
      .withSubscriptions(Set(ApiIdentifier(apiContext, apiVersion)))
      .withFieldValues(buildSubscriptionFieldValues(apiContext, apiVersion, fields))
  }
}
