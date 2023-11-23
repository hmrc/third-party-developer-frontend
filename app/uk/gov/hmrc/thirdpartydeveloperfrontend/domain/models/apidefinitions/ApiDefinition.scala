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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.{SubscriptionFieldValue, SubscriptionFieldsWrapper}

// TODO - 5090 - Add new open access class
case class APISubscriptionStatus(
    name: String,
    serviceName: ServiceName,
    context: ApiContext,
    apiVersion: ApiVersion,
    subscribed: Boolean,
    requiresTrust: Boolean,
    fields: SubscriptionFieldsWrapper,
    isTestSupport: Boolean = false
  ) {

  def isPrivate: Boolean = {
    apiVersion.access.accessType match {
      case ApiAccessType.PRIVATE => true
      case ApiAccessType.PUBLIC  => false
    }
  }

  lazy val apiIdentifier: ApiIdentifier = ApiIdentifier(context, apiVersion.versionNbr)
}

case class APISubscriptionStatusWithSubscriptionFields(
    name: String,
    context: ApiContext,
    apiVersion: ApiVersion,
    fields: SubscriptionFieldsWrapper
  )

case class APISubscriptionStatusWithWritableSubscriptionField(
    name: String,
    context: ApiContext,
    apiVersion: ApiVersion,
    subscriptionFieldValue: SubscriptionFieldValue,
    oldValues: SubscriptionFieldsWrapper
  )

object APISubscriptionStatusWithSubscriptionFields {

  def apply(fields: List[APISubscriptionStatus]): List[APISubscriptionStatusWithSubscriptionFields] = {

    def toAPISubscriptionStatusWithSubscriptionFields(apiSubscriptionStatus: APISubscriptionStatus): Option[APISubscriptionStatusWithSubscriptionFields] = {
      if (apiSubscriptionStatus.fields.fields.isEmpty) {
        None
      } else {
        Some(APISubscriptionStatusWithSubscriptionFields(apiSubscriptionStatus.name, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion, apiSubscriptionStatus.fields))
      }
    }

    fields.flatMap(toAPISubscriptionStatusWithSubscriptionFields)
  }
}
