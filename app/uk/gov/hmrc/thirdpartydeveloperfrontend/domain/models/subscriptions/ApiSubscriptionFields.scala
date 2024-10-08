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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.{FieldName, FieldValue}

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
      apiVersion: ApiVersionNbr,
      fields: List[SubscriptionFieldValue]
    )

  sealed trait ServiceSaveSubscriptionFieldsResponse

  sealed trait ConnectorSaveSubscriptionFieldsResponse extends ServiceSaveSubscriptionFieldsResponse

  case object SaveSubscriptionFieldsSuccessResponse extends ConnectorSaveSubscriptionFieldsResponse

  case class SaveSubscriptionFieldsFailureResponse(fieldErrors: Map[String, String]) extends ConnectorSaveSubscriptionFieldsResponse

  case object SaveSubscriptionFieldsAccessDeniedResponse extends ServiceSaveSubscriptionFieldsResponse
}
