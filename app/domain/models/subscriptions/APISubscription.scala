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

import domain.models.apidefinitions.{ApiVersion, APIStatus, APIAccess, ApiVersionDefinition}

case class ApiCategory(value: String) extends AnyVal

object ApiCategory {
  val EXAMPLE = ApiCategory("EXAMPLE")
}

case class VersionSubscription(version: ApiVersionDefinition, subscribed: Boolean)

case class VersionData(status: APIStatus, access: APIAccess)

case class ApiData(
    serviceName: String,
    name: String,
    isTestSupport: Boolean,
    versions: Map[ApiVersion, VersionData],
    categories: List[ApiCategory])
