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

package uk.gov.hmrc.thirdpartydeveloperfrontend.builder

import java.time.{Period, ZoneOffset}
import java.util.UUID.randomUUID
import java.time.LocalDateTime
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{FieldName, FieldValue, Fields}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.UserIdTracker

trait ApplicationBuilder extends CollaboratorTracker {
  self: UserIdTracker =>

  def buildApplication(appOwnerEmail: String): Application = {

    val appId = ApplicationId("appid-" + randomUUID.toString)
    val clientId = ClientId("clientid-" + randomUUID.toString)
    val appOwnerName = "App owner name"

    Application(
      appId,
      clientId,
      s"${appId.value}-name",
      LocalDateTime.now(ZoneOffset.UTC),
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      grantLength = Period.ofDays(547),
      Environment.SANDBOX,
      Some(s"$appId-description"),
      buildCollaborators(Seq(appOwnerEmail)),
      state = ApplicationState.production(appOwnerEmail, appOwnerName, ""),
      access = Standard(
        redirectUris = List("https://red1", "https://red2"),
        termsAndConditionsUrl = Some("http://tnc-url.com")
      )
    )
  }

  def buildCollaborators(emails: Seq[String]): Set[Collaborator] = {
    emails.map(email => email.asAdministratorCollaborator).toSet
  }

  def buildApplicationWithSubscriptionData(appOwnerEmail: String): ApplicationWithSubscriptionData = {
    val application = buildApplication(appOwnerEmail)

    ApplicationWithSubscriptionData(application)
  }


  def buildSubscriptions(apiContext: ApiContext, apiVersion: ApiVersion): Set[ApiIdentifier] = 
    Set(
      ApiIdentifier(apiContext, apiVersion)
    )

  def buildSubscriptionFieldValues(apiContext: ApiContext, apiVersion: ApiVersion, fields: Fields.Alias): Map[ApiContext, Map[ApiVersion, Fields.Alias]] = {
    Map(apiContext -> Map(apiVersion -> fields))
  }

  def buildApplicationWithSubscriptionData(apiContext: ApiContext = ApiContext.random,
                                          apiVersion: ApiVersion = ApiVersion.random,
                                          fields: Fields.Alias = Map(FieldName.random -> FieldValue.random, FieldName.random -> FieldValue.random)): ApplicationWithSubscriptionData = {
    ApplicationWithSubscriptionData(
      buildApplication("email@example.com"),
      buildSubscriptions(apiContext, apiVersion),
      buildSubscriptionFieldValues(apiContext, apiVersion, fields)
    )
  }
}
