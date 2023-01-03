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

object GlobalSection {

  import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys._

  private def a(em: String)(found: String => String, notFound: () => String) = {
    (globalKeys.contains(em), globalToField.get(em)) match {
      case (true, Some(s)) => found(s)
      case _ => notFound()
    }
  }

  def dataAttribute(errorMessage: String): String = a(errorMessage)(
    k => s"data-global-error-$k", () => "data-global-error-undefined")

  def errorField(errorMessage: String): String = a(errorMessage)(
    k => s"$k", () => "#section-undefined")

  def anchor(errorMessage: String): String = a(errorMessage)(
    k => s"#$k", () => "#section-undefined")
}


