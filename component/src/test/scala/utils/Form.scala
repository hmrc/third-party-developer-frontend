/*
 * Copyright 2025 HM Revenue & Customs
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

import org.openqa.selenium.WebDriver
import org.scalatestplus.selenium.WebBrowser

import uk.gov.hmrc.selenium.webdriver.Driver

object Form extends WebBrowser {

  def populate(a: Map[String, String]) = {
    implicit val wd: WebDriver = Driver.instance

    a.foreach {
      case (field, value) if field.contains("rememberMe")           =>
        val f = field.replaceAll(" ", "")
        if (java.lang.Boolean.parseBoolean(value)) checkbox(f).select()
      case (field, value) if field.toLowerCase.contains("password") =>
        val f = field.replaceAll(" ", "")
        pwdField(f).value = value
      case (field, value)                                           =>
        val f = field.replaceAll(" ", "")
        populateFormField(f, value)
    }
  }

  private def populateFormField(fieldName: String, value: String)(implicit wd: WebDriver) = {
    try {
      textField(fieldName).value = value
    } catch {
      case _: Throwable => textArea(fieldName).value = value
    }
  }
}
