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

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, _}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.subscriptionfields.domain.models._

trait ApplicationBuilder extends FixedClock with ApplicationStateHelper with ApplicationWithCollaboratorsFixtures {

  def buildApplication(appOwnerEmail: LaxEmailAddress): ApplicationWithCollaborators = {

    val appId        = ApplicationId.random
    val appOwnerName = "App owner name"
    val access       = Access.Standard(
      redirectUris = List(LoginRedirectUri.unsafeApply("https://red1"), LoginRedirectUri.unsafeApply("https://red2")),
      termsAndConditionsUrl = Some("http://tnc-url.com")
    )

    standardApp
      .withId(appId)
      .withEnvironment(Environment.SANDBOX)
      .withState(InState.production(appOwnerEmail.text, appOwnerName, ""))
      .withAccess(access)
      .modify(_.copy(name = ApplicationName(s"${appId.toString()}-name")))
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
