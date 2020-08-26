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

package domain.services

import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldsPutRequest
import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition

trait FieldsJsonFormatters {
  import ApplicationJsonFormatters._
  import AccessRequirementsJsonFormatters._

  import play.api.libs.json._

  implicit val readsSubscriptionFieldDefinition: Reads[SubscriptionFieldDefinition] = Json.reads[SubscriptionFieldDefinition]
  implicit val formatSubscriptionFieldsPutRequest: Format[SubscriptionFieldsPutRequest] = Json.format[SubscriptionFieldsPutRequest]
}

object FieldsJsonFormatters extends FieldsJsonFormatters
