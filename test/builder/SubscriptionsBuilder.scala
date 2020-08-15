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

import domain.models.apidefinitions.{APIStatus, APISubscriptionStatus, ApiVersionDefinition}
import domain.models.applications.Application
import domain.models.subscriptions.{AccessRequirements, APISubscription}
import domain.models.subscriptions.ApiSubscriptionFields.{SubscriptionFieldDefinition, SubscriptionFieldsWrapper, SubscriptionFieldValue}
import domain.models.apidefinitions.{ApiContext, ApiVersion}

trait SubscriptionsBuilder {

  def buildSubscription(name: String) = {
    APISubscription(name, s"service-$name", ApiContext(s"context-$name"), Seq.empty, Some(false), false)
  }

  def buildAPISubscriptionStatus(name: String, context: Option[ApiContext] = None, fields: Option[SubscriptionFieldsWrapper] = None): APISubscriptionStatus = {

    val contextName = context.getOrElse(ApiContext(s"context-$name"))
    val version = ApiVersionDefinition(ApiVersion("version"), APIStatus.STABLE)

    val f = fields.getOrElse(SubscriptionFieldsWrapper("fake-appId", "fake-clientId", contextName, version.version, Seq.empty))

    APISubscriptionStatus(name, s"serviceName-$name", contextName, version, subscribed = true, requiresTrust = false, fields = f, isTestSupport = false)
  }

  def emptySubscriptionFieldsWrapper(applicationId: String, clientId: String, context: ApiContext, version: ApiVersion) = {
    SubscriptionFieldsWrapper(applicationId, clientId, context, version, Seq.empty)
  }

  def buildSubscriptionFieldsWrapper(application: Application, fields: Seq[SubscriptionFieldValue]) = {

    val applicationId = application.id

    SubscriptionFieldsWrapper(applicationId, s"clientId-$applicationId", ApiContext(s"context-$applicationId"), ApiVersion(s"version-$applicationId"), fields = fields)
  }

  def buildSubscriptionFieldValue(name: String, value: Option[String] = None, accessRequirements: AccessRequirements = AccessRequirements.Default): SubscriptionFieldValue = {

    val definitnion = SubscriptionFieldDefinition(name, s"description-$name", s"hint-$name", "STRING", s"shortDescription-$name", accessRequirements)

    SubscriptionFieldValue(definitnion, value.getOrElse(s"value-$name"))
  }
}
