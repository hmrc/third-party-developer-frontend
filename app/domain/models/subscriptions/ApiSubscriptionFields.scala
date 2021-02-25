/*
 * Copyright 2021 HM Revenue & Customs
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

package domain.models.subscriptions

import domain.models.apidefinitions.{ApiContext, ApiVersion}
import domain.models.applications.{ApplicationId, ClientId}

object ApiSubscriptionFields {

  case class SubscriptionFieldDefinition(
      name: FieldName,
      description: String,
      shortDescription: String,
      hint: String,
      `type`: String,
      access: AccessRequirements
  )

  case class SubscriptionFieldValue(definition: SubscriptionFieldDefinition, value: FieldValue)

  sealed trait FieldsDeleteResult

  case object FieldsDeleteSuccessResult extends FieldsDeleteResult

  case object FieldsDeleteFailureResult extends FieldsDeleteResult

  case class SubscriptionFieldsWrapper(
      applicationId: ApplicationId,
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      fields: List[SubscriptionFieldValue]
  )

  sealed trait ServiceSaveSubscriptionFieldsResponse

  sealed trait ConnectorSaveSubscriptionFieldsResponse extends ServiceSaveSubscriptionFieldsResponse

  case object SaveSubscriptionFieldsSuccessResponse extends ConnectorSaveSubscriptionFieldsResponse

  case class SaveSubscriptionFieldsFailureResponse(fieldErrors: Map[String, String]) extends ConnectorSaveSubscriptionFieldsResponse

  case object SaveSubscriptionFieldsAccessDeniedResponse extends ServiceSaveSubscriptionFieldsResponse
}
