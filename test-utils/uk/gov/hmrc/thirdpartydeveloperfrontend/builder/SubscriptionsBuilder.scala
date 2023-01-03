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

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ClientId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{AccessRequirements, FieldName, FieldValue}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.{SubscriptionFieldDefinition, SubscriptionFieldValue, SubscriptionFieldsWrapper}

trait SubscriptionsBuilder {

  def buildAPISubscriptionStatus(name: String, context: Option[ApiContext] = None, fields: Option[SubscriptionFieldsWrapper] = None): APISubscriptionStatus = {

    val contextName = context.getOrElse(ApiContext(s"context-$name"))
    val version = ApiVersionDefinition(ApiVersion("version"), APIStatus.STABLE)

    val f = fields.getOrElse(SubscriptionFieldsWrapper(ApplicationId("fake-appId"), ClientId("fake-clientId"), contextName, version.version, List.empty))

    APISubscriptionStatus(name, s"serviceName-$name", contextName, version, subscribed = true, requiresTrust = false, fields = f, isTestSupport = false)
  }

  def emptySubscriptionFieldsWrapper(applicationId: ApplicationId, clientId: ClientId, context: ApiContext, version: ApiVersion) = {
    SubscriptionFieldsWrapper(applicationId, clientId, context, version, List.empty)
  }

  def buildSubscriptionFieldsWrapper(application: Application, fields: List[SubscriptionFieldValue]) = {

    val applicationId = application.id

    SubscriptionFieldsWrapper(
      applicationId,
      ClientId(s"clientId-$applicationId"),
      ApiContext(s"context-$applicationId"),
      ApiVersion(s"version-$applicationId"),
      fields = fields
    )
  }

  def buildSubscriptionFieldValue(name: String, value: Option[String] = None, accessRequirements: AccessRequirements = AccessRequirements.Default, hintOverride: Option[String] = None): SubscriptionFieldValue = {
    val hint = hintOverride.getOrElse(s"hint-$name")
    val definition = SubscriptionFieldDefinition(FieldName(name), s"description-$name", s"shortDescription-$name", hint, "STRING", accessRequirements)

    SubscriptionFieldValue(definition, FieldValue(value.getOrElse(s"value-$name")))
  }
}
