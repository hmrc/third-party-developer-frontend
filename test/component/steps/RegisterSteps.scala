/*
 * Copyright 2018 HM Revenue & Customs
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

package component.steps

import component.matchers.CustomMatchers
import component.pages._
import component.stubs.DeveloperStub
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import domain.Registration
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{By, WebDriver}
import org.scalatest.Matchers
import org.scalatest.selenium.WebBrowser

import scala.collection.JavaConversions._
import scala.collection.mutable

object Form extends WebBrowser {

  def populate(a: mutable.Map[String, String])(implicit driver: WebDriver) = a.foreach {
    case (field, value) if field.toLowerCase.contains("password") =>
      val f = field.replaceAll(" ", "")
      pwdField(f).value = value
    case (field, value) =>
      val f = field.replaceAll(" ", "")
      populateFormField(f, value)
  }

  def populateNameArray(name: String, values: Seq[String])(implicit driver: WebDriver) = {
    val formElements = NameQuery(name).findAllElements.toSeq.filter(_.isDisplayed)
    val elements = formElements.map(element => new TextField(element.underlying))
    val elementsAndValues = elements.zip(values)

    elementsAndValues.foreach { case (element, value) =>
      element.value = value
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

class RegisterSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers {

  import scala.collection.JavaConverters._

  implicit val webDriver: WebDriver = Env.driver


  Given( """^I enter valid information for all fields:$""") { (registrationDetails: DataTable) =>
    val data: mutable.Map[String, String] = registrationDetails.asMap(classOf[String], classOf[String]).asScala
    DeveloperStub.register(createPayload(data), 201)
    Form.populate(data)
  }

  def createPayload(data: mutable.Map[String, String]): Registration = {
    Registration(data("first name"), data("last name"), data("email address"), data("password"))
  }

  Given( """^I enter valid information for all fields with an email already in use$""") { (registrationDetails: DataTable) =>
    val data: mutable.Map[String, String] = registrationDetails.asMap(classOf[String], classOf[String]).asScala
    DeveloperStub.register(createPayload(data), 409)
    Form.populate(data)
  }

  Given( """^I enter valid information for all fields generating '(.*)' response$""") { (status: String, registrationDetails: DataTable) =>
    val data: mutable.Map[String, String] = registrationDetails.asMap(classOf[String], classOf[String]).asScala

    val code = status match {
      case "Bad request" => 400
      case "Not found" => 404
      case "Internal server error" => 500
      case "Ok" => 200
    }

    DeveloperStub.register(createPayload(data), code)
    Form.populate(data)
  }

  Given( """^I enter all the fields with an email exceeding 320 characters:$""") { (data: DataTable) =>
    val form: mutable.Map[String, String] = data.asMap(classOf[String], classOf[String]).asScala
    val email = ("abc" * 105) + "@example.com"
    Form.populate(form ++ mutable.Map("emailaddress" -> email))
  }

  Given("""^I expect a resend call from '(.*)'$""") {
    email: String => {
      DeveloperStub.setupResend(email, 204)
    }
  }

  When( """^I click on submit$""") { () =>
    val element = webDriver.findElement(By.id("submit"))
    val actions = new Actions(webDriver)
    actions.moveToElement(element)
    actions.click()
    actions.perform()
  }

  When( """^I click on cancel""") { () =>
    val element = webDriver.findElement(By.id("cancel"))
    val actions = new Actions(webDriver)
    actions.moveToElement(element)
    actions.click()
    actions.perform()
  }

  Then( """^I see the global error '(.*)' for the '(.*)'$""") { (errorMessage: String, fieldName: String) =>
    val fn = fieldName.toLowerCase.replace(" ", "")
    withClue(s"The field '$fieldName' does not have global error: '$errorMessage'") {
      val global = RegistrationPage.globalError(fn)
      global.getText shouldBe errorMessage
      global.getAttribute("href") should include(s"#$fn")
      RegistrationPage.hasElementWithId(fn) shouldBe true
    }
  }

  Then( """^I see all global errors:$""") { (errors: DataTable) =>
    val e: mutable.Map[String, String] = errors.asMap(classOf[String], classOf[String]).asScala
    val globalErrors = RegistrationPage.globalErrors
    withClue(s"Errors expected:${e.size}  but real ones: ${globalErrors.size()}") {
      e.size shouldBe globalErrors.size()
    }

    val zip = e zip globalErrors
    zip.foreach {
      case ((field, errorMessage), we) =>
        withClue(s"The field '$field' does not have global error: '$errorMessage'") {
          val fn = field.toLowerCase.replace(" ", "")
          we.getText shouldBe errorMessage
          we.getAttribute("href") should include(s"#$fn")
          RegistrationPage.hasElementWithId(fn) shouldBe true
        }
    }
  }

  Given( """^A third party developer has completed the registration form with the email '(.*)'$""") { (emailAddress: String) => }

  Then( """^I see error '(.*)' with a link to '(.*)' page with href '(.*)' for the field '(.*)'$""") {
    (error: String, page: String, link: String, fieldName: String) =>
      withClue(s"The field '$fieldName' does not have field error: '$error'") {
        val tag = fieldName.toLowerCase.replace(" ", "")
        val fieldError = CurrentPage.fieldError(tag)
        fieldError.getText shouldBe s"$error"

        val findElement = fieldError.findElement(By.cssSelector(s"[data-link-error-$tag]"))
        findElement.getAttribute("href") should include(link)
      }
  }

  Given( """^I am registered$""") {
    DeveloperStub.setupVerification("verificationCode", 201)
  }

  When( """I click on the verification link""") {
    go(VerificationLink("verificationCode"))
  }

  And( """I am verified""") {
    DeveloperStub.verifyVerification("verificationCode")
  }

  When( """^I click on an invalid verification link$""") {
    DeveloperStub.setupVerification("verificationCode", 400)
    go(VerificationLink("verificationCode"))
  }

  Then("user is displayed message that '(.*)'$") { (actualText: String) =>
    val expectedText: String = eventually {
      webDriver.findElement(By.cssSelector("div.sandbox--visible.alert.alert--info > p")).getText
    }
    withClue(s"Register page does not contain Text --> This service should only be used for testing your applications. For production credentials, go to the Developer Hub") {
      expectedText shouldBe actualText
    }
  }
}

