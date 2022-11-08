/*
 * Copyright 2020 HM Revenue & Customs
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
import io.cucumber.scala.Implicits._
import matchers.CustomMatchers
import org.openqa.selenium.{By, WebDriver, WebElement}
import org.openqa.selenium.interactions.Actions
import pages._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers

object TableMisuseAdapters {
  def asListOfKV(dataTable: DataTable): Map[String,String] = {
    dataTable.asScalaRawLists[String].map( _.toList match {
      case a :: b :: c  => a -> b
      case _ => throw new RuntimeException("Badly constructed table")
    }).toMap
  }

  def valuesInColumn(n: Int)(data: DataTable): List[String] = {
    data.asLists().asScala.map(_.get(n)).toList
  }
}

class CommonSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers {
  implicit val webDriver: WebDriver = Env.driver

  val pages: Map[String, WebPage] = Map(
    "Registration" -> RegistrationPage,
    "View all applications" -> ManageApplicationPage,
    "Add an application to the sandbox empty nest" -> AddApplicationEmptyPage,
    "Enter Access Code" -> Login2svEnterAccessCodePage,
    "No Applications" -> NoApplicationsPage,
    "Add application success" -> AddApplicationSuccessPage,
    "Sign in" -> SignInPage.default,
    "Email confirmation" -> EmailConfirmationPage,
    "Email preferences" -> EmailPreferencesSummaryPage,
    "Resend confirmation" -> ResendConfirmationPage,
    "Manage profile" -> ManageProfilePage,
    "Change profile details" -> ChangeProfileDetailsPage,
    "Edit password success" -> ChangePasswordSuccessPage,
    "Logout survey" -> SignOutSurveyPage,
    "Account deletion confirmation" -> AccountDeletionConfirmationPage,
    "Account deletion request submitted" -> AccountDeletionRequestSubmittedPage,
    "Recommend Mfa" -> RecommendMfaPage,
    "Recommend Mfa Skip Acknowledge" -> RecommendMfaSkipAcknowledgePage,
    "Protect Account" -> ProtectAccountPage,
    "Setup 2SV QR" -> Setup2svQrPage,
    "Setup 2SV Enter Access Code" -> Setup2svEnterAccessCodePage,
    "Protect Account Complete" -> ProtectAccountCompletePage,
    "Account protection" -> AccountProtectionPage,
    "Confirm 2SV removal"-> MfaConfirmRemovalPage,
    "2SV remove"-> MfaRemovePage,
    "2SV removal complete" -> MfaRemovalCompletePage,
    "Password reset confirmation" -> PasswordResetConfirmationPage,
    "You have reset your password" -> YouHaveResetYourPasswordPage,
    "Reset password link no longer valid" -> ResetPasswordLinkNoLongerValidPage,
    "Authenticator App Start Page" -> AuthAppStartPage,
    "Create name for Authenticator App" -> CreateNameForAuthAppPage
  )

  Given( """^I navigate to the '(.*)' page$""") { (pageName: String) =>
    withClue(s"Fail to load page: $pageName")(goOn(pages(pageName)))
  }

  Given( """^I enter all the fields:$""") { (data: DataTable) =>
    val form: Map[String,String] = data.asScalaRawMaps[String,String].head
    Form.populate(form)
  }

  Then( """^I am on the '(.*)' page$""") { (pageName: String) =>
    eventually {
    withClue(s"Fail to be on page: $pageName")(on(pages(pageName))) }
  }

  Then( """^the user-nav header contains a '(.*)' link""") { (linkText: String) =>
    val header = webDriver.findElement(By.id("proposition-links"))
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
    val textsToFind: List[String] = TableMisuseAdapters.valuesInColumn(0)(labels)
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
    val textsToFind = TableMisuseAdapters.valuesInColumn(0)(labels)
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
