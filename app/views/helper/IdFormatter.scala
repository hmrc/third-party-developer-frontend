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

package views.helper

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiIdentifier, ApiVersionNbr}

object IdFormatter {
  def replaceNonAlphaNumeric(str: String, replacement: String = "_") = { str.replaceAll("\\W", replacement) }

  def identifier(apiIdentifier: ApiIdentifier): String = identifier(apiIdentifier.context, apiIdentifier.versionNbr)

  def identifier(context: ApiContext, apiVersion: ApiVersionNbr): String = contextSuffix(context, apiVersion.value)

  def context(context: ApiContext) = { s"${replaceNonAlphaNumeric(context.value)}" }

  def contextSuffix(context: ApiContext, suffix: String) = { s"${replaceNonAlphaNumeric(context.value)}-${replaceNonAlphaNumeric(suffix)}" }
}
