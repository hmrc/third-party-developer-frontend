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

import scala.collection.mutable
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

import io.cucumber.datatable.DataTable
import io.cucumber.scala.Implicits._
import io.cucumber.scala.{EN, ScalaDsl}
import matchers.CustomMatchers
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{By, WebElement}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import pages._
import utils.BrowserDriver

import uk.gov.hmrc.selenium.webdriver.Driver

object TableMisuseAdapters {

  def asListOfKV(dataTable: DataTable): Map[String, String] = {
    dataTable.asScalaRawLists[String].map(_.toList match {
      case a :: b :: c => a -> b
      case _           => throw new RuntimeException("Badly constructed table")
    }).toMap
  }

  def valuesInColumn(n: Int)(data: DataTable): List[String] = {
    data.asLists().asScala.map(_.get(n)).toList
  }
}

class CommonSteps extends ScalaDsl with EN with Matchers with OptionValues with NavigationSugar with CustomMatchers with BrowserDriver {

  val mfaPages = Map(
    "Authenticator App Start Page"         -> AuthAppStartPage,
    "Create name for Authenticator App"    -> CreateNameForAuthAppPage,
    "Authenticator App Mfa Setup Reminder" -> AuthAppSetupReminderPage,
    "Authenticator App Setup Skipped"      -> AuthenticatorAppSetupSkippedPage,
    "Authenticator App Setup Complete"     -> AuthenticatorAppSetupCompletePage,
    "Authenticator App Login Access Code"  -> AuthAppLoginAccessCodePage.page,
    "Authenticator App Access Code"        -> AuthenticatorAppAccessCodePage,
    "Select MFA"                           -> SelectMfaPage,
    "Sms mobile number"                    -> SmsMobileNumberPage,
    "Sms Mfa Setup Skipped"                -> SmsSetupSkippedPage,
    "Sms Mfa Setup Reminder"               -> SmsSetupReminderPage,
    "Sms Access Code"                      -> SmsAccessCodePage,
    "Sms Login Access Code"                -> SmsLoginAccessCodePage.page,
    "Sms Setup Complete"                   -> SmsSetupCompletePage,
    "Security preferences"                 -> SecurityPreferencesPage,
    "2SV removal complete"                 -> MfaRemovalCompletePage,
    "Setup 2SV QR"                         -> Setup2svQrPage
  )

  val pages: Map[String, WebPage] = Map(
    "Registration"                                 -> RegistrationPage,
    "View all applications"                        -> ManageApplicationPage,
    "Add an application to the sandbox empty nest" -> AddApplicationEmptyPage,
    "No Applications"                              -> NoApplicationsPage,
    "Add application success"                      -> AddApplicationSuccessPage,
    "Sign in"                                      -> SignInPage.default,
    "Email confirmation"                           -> EmailConfirmationPage,
    "Email preferences"                            -> EmailPreferencesSummaryPage,
    "Resend confirmation"                          -> ResendConfirmationPage,
    "Manage profile"                               -> ManageProfilePage,
    "Change profile details"                       -> ChangeProfileDetailsPage,
    "Edit password success"                        -> ChangePasswordSuccessPage,
    "Logout survey"                                -> SignOutSurveyPage,
    "Account deletion confirmation"                -> AccountDeletionConfirmationPage,
    "Account deletion request submitted"           -> AccountDeletionRequestSubmittedPage,
    "Recommend Mfa"                                -> RecommendMfaPage,
    "Recommend Mfa Skip Acknowledge"               -> RecommendMfaSkipAcknowledgePage,
    "Password reset confirmation"                  -> PasswordResetConfirmationPage,
    "You have reset your password"                 -> YouHaveResetYourPasswordPage,
    "Reset password link no longer valid"          -> ResetPasswordLinkNoLongerValidPage
  ) ++ mfaPages

  Given("""^I navigate to the '(.*)' page$""") { (pageName: String) =>
    withClue(s"Fail to load page: $pageName")(goOn(pages(pageName)))
  }

  Given("""^I enter all the fields:$""") { (data: DataTable) =>
    val form: Map[String, String] = data.asScalaRawMaps[String, String].head
    Form.populate(form)
  }

  Then("""^I am on the '(.*)' page$""") { (pageName: String) =>
    eventually {
      withClue(s"Fail to be on page: $pageName")(on(pages(pageName)))
    }
  }

  Then("""^the user-nav header contains a '(.*)' link""") { (linkText: String) =>
    val header = driver.findElement(By.id("proposition-links"))
    header.findElement(By.linkText(linkText)).isDisplayed shouldBe true
  }

  Then("""^The current page contains link '(.*)' to '(.*)'$""") { (linkText: String, pageName: String) =>
    val href = CurrentPage.linkTextHref(linkText)
    val page = withClue(s"page not found: $pageName")(pages(pageName))
    href.value shouldBe page.url()
  }

  Then("""^I see text in fields:$""") { (fieldData: DataTable) =>
    verifyData(fieldData, _.getText, By.id)
  }

  Then("""^I see:$""") { (labels: DataTable) =>
    val textsToFind: List[String] = TableMisuseAdapters.valuesInColumn(0)(labels)
    eventually {
      CurrentPage.bodyText() should containInOrder(textsToFind)
    }
  }

  def verifyData(fieldData: DataTable, f: WebElement => String, selector: String => By): Unit = {
    val keyValues: mutable.Map[String, String] = fieldData.asMap(classOf[String], classOf[String]).asScala
    val body                                   = Driver.instance.findElement(By.tagName("body"))
    keyValues.foreach(keyValue =>
      withClue(s"The field '${keyValue._1}' does not have value: '${keyValue._2}'") {
        eventually(timeout(1 second)) { f(body.findElement(selector(keyValue._1))) shouldBe keyValue._2 }
      }
    )
  }

  Then("""^I see on current page:$""") { (labels: DataTable) =>
    val textsToFind = TableMisuseAdapters.valuesInColumn(0)(labels)
    Driver.instance.findElement(By.tagName("body")).getText should containInOrder(textsToFind)
  }

  When("""^I click on the '(.*)' link$""") { linkText: String =>
    val link    = driver.findElement(By.linkText(linkText))
    val actions = new Actions(driver)
    actions.moveToElement(link)
    actions.click()
    actions.perform()
  }

  When("""^I click on the button with id '(.*)'$""") { id: String =>
    val link    = driver.findElement(By.id(id))
    val actions = new Actions(driver)
    actions.moveToElement(link)
    actions.click()
    actions.perform()
  }

  When("""^I click on the '(.*)' button""") { buttonText: String =>
    val element = driver.findElement(By.xpath(s"//button[text()='$buttonText']"))
    val actions = new Actions(driver)
    actions.moveToElement(element)
    actions.click()
    actions.perform()
  }

  When("""^I click on the radio button with id '(.*)'""") { id: String =>
    val button = driver.findElement(By.id(id))
    button.click()
  }
}
