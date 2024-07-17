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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences

import play.api.libs.json.{Json, OFormat}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiCategory

// TODO - make category an APICategory
case class APICategoryDisplayDetails(category: String, name: String) {

  def toAPICategory(): ApiCategory = {
    ApiCategory.unsafeApply(category)
  }
}

object APICategoryDisplayDetails {
  implicit val formatApiCategory: OFormat[APICategoryDisplayDetails] = Json.format[APICategoryDisplayDetails]

  def from(category: ApiCategory): APICategoryDisplayDetails = APICategoryDisplayDetails(category.toString, category.displayText)
}