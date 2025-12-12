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

import org.openqa.selenium.By

trait FormPage extends WebPage {
  def dataError(name: String) = dataAttribute(s"[data-$name]")

  def fieldError(fieldName: String) = dataAttribute(s"[data-field-error-$fieldName]")

  def globalError(fieldName: String) = dataAttribute(s"[data-global-error-$fieldName]")

  private def dataAttribute(className: String) = findElement(By.cssSelector(className)).get

  def globalErrors = findElements(By.cssSelector("[data-global-error]"))

  def hasElementWithId(id: String) = findElements(By.id(id)).size == 1
}
