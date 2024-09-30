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

package steps

import io.cucumber.datatable.DataTable
import io.cucumber.scala.{EN, ScalaDsl}
import matchers.CustomMatchers
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{By, WebDriver}
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.selenium.WebBrowser
import pages._
import stubs.DeveloperStub
import utils.BrowserDriver

import play.api.http.Status

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.RegistrationRequest

object Form extends WebBrowser {

  def populate(a: Map[String, String])(implicit driver: WebDriver) = a.foreach {
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

  private def populateFormField(fieldName: String, value: String)(implicit wd: WebDriver) = {
    try {
      textField(fieldName).value = value
    } catch {
      case _: Throwable => textArea(fieldName).value = value
    }
  }
}

class RegisterSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers with BrowserDriver {

  Given("""^I enter valid information for all fields:$""") { (registrationDetails: DataTable) =>
    import io.cucumber.scala.Implicits._

    val data: Map[String, String] = registrationDetails.asScalaRawMaps[String, String].head
    DeveloperStub.register(createPayload(data), Status.CREATED)
    Form.populate(data)
  }

  def createPayload(data: Map[String, String]): RegistrationRequest = {
    RegistrationRequest(data("email address").toLaxEmail, data("password"), data("first name"), data("last name"))
  }

  Given("""^I expect a resend call from '(.*)'$""") {
    email: String =>
      {
        DeveloperStub.setupResend(email.toLaxEmail, Status.NO_CONTENT)
      }
  }

  When("""^I click on submit$""") { () =>
    val element = driver.findElement(By.id("submit"))
    new Actions(driver)
      .moveToElement(element)
      .click()
      .perform()
  }
}
