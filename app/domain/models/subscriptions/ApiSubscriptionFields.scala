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

package domain.models.subscriptions

import play.api.libs.json.{Format, Json}
import domain.models.apidefinitions.{ApiContext, ApiVersion}
import domain.models.applications.{ClientId, ApplicationId}

object ApiSubscriptionFields {

  case class SubscriptionFieldDefinition(
      name: String,
      description: String,
      shortDescription: String,
      hint: String,
      `type`: String,
      access: AccessRequirements
  )

  case class SubscriptionFieldValue(definition: SubscriptionFieldDefinition, value: String)

  sealed trait FieldsDeleteResult

  case object FieldsDeleteSuccessResult extends FieldsDeleteResult

  case object FieldsDeleteFailureResult extends FieldsDeleteResult

  case class SubscriptionFieldsWrapper(
      applicationId: ApplicationId,
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      fields: Seq[SubscriptionFieldValue]
  )

  type Fields = Map[String, String]

  object Fields {
    val empty = Map.empty[String, String]
  }

  case class SubscriptionFieldsPutRequest(
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      fields: Map[String, String]
  )

  object SubscriptionFieldsPutRequest {
    implicit val format: Format[SubscriptionFieldsPutRequest] =
      Json.format[SubscriptionFieldsPutRequest]
  }

  sealed trait ServiceSaveSubscriptionFieldsResponse

  sealed trait ConnectorSaveSubscriptionFieldsResponse extends ServiceSaveSubscriptionFieldsResponse

  case object SaveSubscriptionFieldsSuccessResponse extends ConnectorSaveSubscriptionFieldsResponse

  case class SaveSubscriptionFieldsFailureResponse(fieldErrors: Map[String, String]) extends ConnectorSaveSubscriptionFieldsResponse

  case object SaveSubscriptionFieldsAccessDeniedResponse extends ServiceSaveSubscriptionFieldsResponse

}
