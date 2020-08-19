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

package domain.models.applications

import domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}

case class FieldName(value: String) extends AnyVal

object FieldName {
  import play.api.libs.json.{Json, JsSuccess, KeyReads, KeyWrites}
  implicit val formatFieldName = Json.valueFormat[FieldName]
  implicit val keyReadsFieldName: KeyReads[FieldName] = key => JsSuccess(FieldName(key))
  implicit val keyWritesFieldName: KeyWrites[FieldName] = _.value
}

case class FieldValue(value: String) extends AnyVal
object FieldValue {
  import play.api.libs.json.Json
  val formatFieldValue = Json.valueFormat[FieldValue]
}

case class ApplicationWithSubscriptionData(
    application: Application,
    subscriptions: Set[ApiIdentifier] = Set.empty,
    subscriptionFieldValues: Map[ApiContext, Map[ApiVersion, Map[FieldName, FieldValue]]] = Map.empty
)