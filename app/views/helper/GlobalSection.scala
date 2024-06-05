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
  import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FieldNameKey
  import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.GlobalMessageKey

  private def a(rawErrorMessageKeyOrMessageText: String)(found: FieldNameKey => String, notFound: () => String) = {
    globalKeys.find(_.value == rawErrorMessageKeyOrMessageText).fold(
      notFound()
    )( key =>
      globalToField.get(key).fold(
        notFound()
      )(
        field => found(field)
      )
    )
  }

  def dataAttribute(rawErrorMessageKeyOrMessageText: String): String = a(rawErrorMessageKeyOrMessageText)(
    k => s"data-global-error-$k",
    () => "data-global-error-undefined"
  )

  def errorField(rawErrorMessageKeyOrMessageText: String): String = a(rawErrorMessageKeyOrMessageText)(
    k => s"$k",
    () => "#section-undefined"
  )

  def anchor(rawErrorMessageKeyOrMessageText: String): String = a(rawErrorMessageKeyOrMessageText)(
    k => s"#$k",
    () => "#section-undefined"
  )
}
