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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{CombinedApi, CombinedApiCategory, ExtendedApiDefinition}

trait ExtendedApiDefinitionTestDataHelper {
  def extendedApiDefinition(name: String): ExtendedApiDefinition = extendedApiDefinition(name, List("category"))

  def extendedApiDefinition(name: String, categories: List[String]) = ExtendedApiDefinition(name, name, name, ApiContext(name), categories)
}

trait CombinedApiTestDataHelper {
  def combinedApi(name: String): CombinedApi = combinedApi(name, List(CombinedApiCategory("category")))

  def combinedApi(name: String, categories: List[CombinedApiCategory]) = CombinedApi(name, name, categories, REST_API)
}
