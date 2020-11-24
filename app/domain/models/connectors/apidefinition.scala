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

package domain.models.connectors

import domain.models.apidefinitions.ApiContext
import play.api.libs.json.Json

/*
 * This version of ApiDefinition currently covers everything we're interested in for TPDFE. api-platform-microservice can actually return one of two (similar, but not the same)
 * API Definition types - ApiDefinition or ExtendedApiDefinition. We're currently only interested in elements that are common to both, hence the single type here.
 */
case class ApiDefinition(serviceName: String, name: String, description: String, context: ApiContext, categories: Seq[String])

object ApiDefinition {
    implicit val formatApiDefinition = Json.format[ApiDefinition]
}