/*
 * Copyright 2019 HM Revenue & Customs
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
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{By, WebDriver, WebElement}
import org.scalatest.Matchers

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration._

class CommonSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers {

  import scala.collection.JavaConverters._

  implicit val webDriver: WebDriver = Env.driver

  val pages: Map[String, WebPage] = Map(
    "Registration" -> RegistrationPage,
    "View all applications" -> ManageApplicationPage,
    "Manage applications empty nest" -> ManageApplicationEmptyPage,
    "Add application" -> AddApplicationPage,
    "Add application success" -> AddApplicationSuccessPage,
    "Sign in" -> SignInPage.default,
    "Email confirmation" -> EmailConfirmationPage,
    "Resend confirmation" -> ResendConfirmationPage,
    "Manage profile" -> ManageProfilePage,
    "Change profile details" -> ChangeProfileDetailsPage,
    "Edit password success" -> ChangePasswordSuccessPage,
    "Logout survey" -> SignOutSurveyPage,
    "Account deletion confirmation" -> AccountDeletionConfirmationPage,
    "Account deletion request submitted" -> AccountDeletionRequestSubmittedPage,
    "Recommend Mfa" -> RecommendMfaPage,
    "Recommend Mfa Skip Acknowledge" -> RecommendMfaSkipAcknowledgePage
  )

  Given( """^I navigate to the '(.*)' page$""") { (pageName: String) =>
    withClue(s"Fail to load page: $pageName")(goOn(pages(pageName)))
  }

  Given( """^I enter all the fields:$""") { (data: DataTable) =>
    val form: mutable.Map[String, String] = data.asMap(classOf[String], classOf[String]).asScala
    Form.populate(form)
  }

  Then( """^I am on the '(.*)' page$""") { (pageName: String) =>
    eventually {
    withClue(s"Fail to be on page: $pageName")(on(pages(pageName))) }
  }

  Then( """^the user-nav header contains a '(.*)' link""") { (linkText: String) =>
    val header = webDriver.findElement(By.id("user-nav-links"))
    header.findElement(By.linkText(linkText)).isDisplayed shouldBe true
  }

  Then( """^The current page contains link '(.*)' to '(.*)'$""") { (linkText: String, pageName: String) =>
    val link: WebElement = Env.driver.findElement(By.linkText(linkText))
    val page = withClue(s"page not found: $pageName")(pages(pageName))
    link.getAttribute("href") shouldBe page.url
  }

  Then( """^I see text in fields:$""") { (fieldData: DataTable) =>
    verifyData(fieldData, _.getText, By.id)
  }

  Then( """^I see:$""") { (labels: DataTable) =>
    val textsToFind = labels.raw().flatten.toList
    eventually {
    CurrentPage.bodyText should containInOrder(textsToFind) }
  }

  def verifyData(fieldData: DataTable, f: WebElement => String, selector: String => By): Unit = {
    val keyValues: mutable.Map[String, String] = fieldData.asMap(classOf[String], classOf[String]).asScala
    val body = Env.driver.findElement(By.tagName("body"))
    keyValues.foreach(keyValue =>
      withClue(s"The field '${keyValue._1}' does not have value: '${keyValue._2}'") {
        eventually (timeout(1 second)){ f(body.findElement(selector(keyValue._1))) shouldBe keyValue._2}
      })
  }

  Then( """^I see on current page:$""") { (labels: DataTable) =>
    val textsToFind = labels.raw().flatten.toList
    Env.driver.findElement(By.tagName("body")).getText should containInOrder(textsToFind)
  }

  When( """^I click on the '(.*)' link$""") { linkText: String =>
    val link = webDriver.findElement(By.linkText(linkText))
    val actions = new Actions(webDriver)
    actions.moveToElement(link)
    actions.click()
    actions.perform()
  }

  When( """^I click on the button with id '(.*)'$""") { id: String =>
    val link = webDriver.findElement(By.id(id))
    val actions = new Actions(webDriver)
    actions.moveToElement(link)
    actions.click()
    actions.perform()
  }

  When( """^I click on the '(.*)' button""") { buttonText: String =>
    val element = webDriver.findElement(By.xpath(s"//button[text()='$buttonText']"))
    val actions = new Actions(webDriver)
    actions.moveToElement(element)
    actions.click()
    actions.perform()
  }

  When( """^I click on the radio button with id '(.*)'""") { id: String =>
    val button = webDriver.findElement(By.id(id))
    button.click()
  }
}
