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

package uk.gov.hmrc.apiplatform.modules.apis.domain.models

import play.api.libs.json.Json

trait HasApiIdentifier {
  def apiIdentifier: ApiIdentifier
}

case class ApiIdentifier(context: ApiContext, version: ApiVersion) {
  def asText(separator: String): String = s"${context.value}$separator${version.value}"
}

object ApiIdentifier {
  implicit val apiIdentifierFormat = Json.format[ApiIdentifier]

  def random = ApiIdentifier(ApiContext.random, ApiVersion.random)

  
  // When we drop 2.12 support we can use : -
  // Ordering.by[ApiIdentifier, String](_.context.value)
  //  .orElseBy(_.version.value)

  implicit val ordering: Ordering[ApiIdentifier] = new Ordering[ApiIdentifier] {

    override def compare(x: ApiIdentifier, y: ApiIdentifier): Int = Ordering.Tuple2[ApiContext, ApiVersion].compare(
      (x.context, x.version),
      (y.context, y.version)
    )
  }
}