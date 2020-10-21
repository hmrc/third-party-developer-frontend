/*
 * Copyright 2020 HM Revenue & Customs
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

package builder

import java.util.UUID.randomUUID

import domain.models.applications._
import uk.gov.hmrc.time.DateTimeUtils
import domain.models.apidefinitions._
import domain.models.subscriptions.{Fields,FieldValue,FieldName}

trait ApplicationBuilder {

  def buildApplication(appOwnerEmail: String): Application = {

    val appId = ApplicationId("appid-" + randomUUID.toString)
    val clientId = ClientId("clientid-" + randomUUID.toString)

    Application(
      appId,
      clientId,
      s"$appId-name",
      DateTimeUtils.now,
      DateTimeUtils.now,
      None,
      Environment.SANDBOX,
      Some(s"$appId-description"),
      buildCollaborators(Seq(appOwnerEmail)),
      state = ApplicationState.production(appOwnerEmail, ""),
      access = Standard(
        redirectUris = Seq("https://red1", "https://red2"),
        termsAndConditionsUrl = Some("http://tnc-url.com")
      )
    )
  }

  def buildCollaborators(emails: Seq[String]): Set[Collaborator] = {
    emails.map(email => Collaborator(email, Role.ADMINISTRATOR)).toSet
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
