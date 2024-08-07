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

import java.time.Period
import java.util.UUID.randomUUID

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{Collaborator, RedirectUri}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, _}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.UserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{FieldName, FieldValue, Fields}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker

trait ApplicationBuilder extends CollaboratorTracker with FixedClock with ApplicationStateHelper {
  self: UserIdTracker =>

  def buildApplication(appOwnerEmail: LaxEmailAddress): Application = {

    val appId        = ApplicationId.random
    val clientId     = ClientId("clientid-" + randomUUID.toString)
    val appOwnerName = "App owner name"

    Application(
      appId,
      clientId,
      s"${appId.toString()}-name",
      instant,
      Some(instant),
      None,
      grantLength = Period.ofDays(547),
      Environment.SANDBOX,
      Some(s"$appId-description"),
      buildCollaborators(Seq(appOwnerEmail)),
      state = InState.production(appOwnerEmail.text, appOwnerName, ""),
      access = Access.Standard(
        redirectUris = List(RedirectUri.unsafeApply("https://red1"), RedirectUri.unsafeApply("https://red2")),
        termsAndConditionsUrl = Some("http://tnc-url.com")
      )
    )
  }

  def buildCollaborators(emails: Seq[LaxEmailAddress]): Set[Collaborator] = {
    emails.map(email => email.asAdministratorCollaborator).toSet
  }

  def buildApplicationWithSubscriptionData(appOwnerEmail: LaxEmailAddress): ApplicationWithSubscriptionData = {
    val application = buildApplication(appOwnerEmail)

    ApplicationWithSubscriptionData(application)
  }

  def buildSubscriptions(apiContext: ApiContext, apiVersion: ApiVersionNbr): Set[ApiIdentifier] =
    Set(
      ApiIdentifier(apiContext, apiVersion)
    )

  def buildSubscriptionFieldValues(apiContext: ApiContext, apiVersion: ApiVersionNbr, fields: Fields.Alias): Map[ApiContext, Map[ApiVersionNbr, Fields.Alias]] = {
    Map(apiContext -> Map(apiVersion -> fields))
  }

  def buildApplicationWithSubscriptionData(
      apiContext: ApiContext = ApiContext.random,
      apiVersion: ApiVersionNbr = ApiVersionNbr.random,
      fields: Fields.Alias = Map(FieldName.random -> FieldValue.random, FieldName.random -> FieldValue.random)
    ): ApplicationWithSubscriptionData = {
    ApplicationWithSubscriptionData(
      buildApplication("email@example.com".toLaxEmail),
      buildSubscriptions(apiContext, apiVersion),
      buildSubscriptionFieldValues(apiContext, apiVersion, fields)
    )
  }
}
