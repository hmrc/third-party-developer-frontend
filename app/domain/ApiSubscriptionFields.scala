/*
 * Copyright 2018 HM Revenue & Customs
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

package domain

import java.util.UUID

import play.api.libs.json.{Format, Json}

object ApiSubscriptionFields {

  type Fields = Map[String, String]

  def fields(tpl: (String, String)*) = Map[String, String](tpl: _*)

  case class FieldDefinitionsResponse(fieldDefinitions: List[SubscriptionField])
  object FieldDefinitionsResponse {
    implicit val format: Format[FieldDefinitionsResponse] = Json.format[FieldDefinitionsResponse]
  }

  case class SubscriptionFieldsWrapper(applicationId: String, clientId: String, apiContext: String, apiVersion: String, fields: Seq[SubscriptionField])

  case class SubscriptionField(name: String, description: String, hint: String, `type`: String, value: Option[String] = None) {
    def withValue(updatedValue: Option[String]): SubscriptionField = {
      copy(name, description, hint, `type`, updatedValue)
    }
  }
  object SubscriptionField {
    implicit val format: Format[SubscriptionField] = Json.format[SubscriptionField]
  }

  case class SubscriptionFields(clientId: String, apiContext: String, apiVersion: String, fieldsId: UUID, fields: Map[String, String])
  object SubscriptionFields {
    implicit val format: Format[SubscriptionFields] = Json.format[SubscriptionFields]
  }

  case class SubscriptionFieldsPutRequest(clientId: String, apiContext: String, apiVersion: String, fields: Map[String, String])
  object SubscriptionFieldsPutRequest {
    implicit val format: Format[SubscriptionFieldsPutRequest] = Json.format[SubscriptionFieldsPutRequest]
  }
}
