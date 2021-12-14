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

package connectors

import domain.services.{ApiDefinitionsJsonFormatters, ApplicationsJsonFormatters, CombinedApiJsonFormatters, SubscriptionsJsonFormatters}

trait ApmConnectorJsonFormatters extends ApiDefinitionsJsonFormatters with ApplicationsJsonFormatters
  with CombinedApiJsonFormatters with  SubscriptionsJsonFormatters {

  import domain.models.subscriptions._
  import play.api.libs.json._

  implicit val readsApiCategory: Reads[ApiCategory] = Json.valueReads[ApiCategory]
  implicit val readsVersionData: Reads[VersionData] = Json.reads[VersionData]
  implicit val readsApiData: Reads[ApiData] = Json.reads[ApiData]
}

object ApmConnectorJsonFormatters extends ApmConnectorJsonFormatters
