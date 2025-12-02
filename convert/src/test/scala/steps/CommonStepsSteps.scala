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

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import org.openqa.selenium.interactions.Actions
import uk.gov.hmrc.selenium.webdriver.Driver
import org.openqa.selenium.By
import org.scalatest.OptionValues
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration
import org.openqa.selenium.support.ui.ExpectedConditions

object CommonStepsSteps extends NavigationSugar with OptionValues with CustomMatchers {
 
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
    "Account deletion confirmation"                -> AccountDeletionConfirmationPage,
    "Account deletion request submitted"           -> AccountDeletionRequestSubmittedPage,
    "Recommend Mfa"                                -> RecommendMfaPage,
    "Recommend Mfa Skip Acknowledge"               -> RecommendMfaSkipAcknowledgePage,
    "Password reset confirmation"                  -> PasswordResetConfirmationPage,
    "You have reset your password"                 -> YouHaveResetYourPasswordPage,
    "Reset password link no longer valid"          -> ResetPasswordLinkNoLongerValidPage
  ) ++ mfaPages

  // ^I navigate to the '(.*)' page$
  def givenINavigateToThePage(pageName: String): Unit = {
    withClue(s"Fail to load page: $pageName")(goOn(pages(pageName)))
  }

  // // ^I enter all the fields:$
  // def givenIEnterAllTheFields(data: DataTable): Unit = {
  //   val form: Map[String, String] = data.asScalaRawMaps[String, String].head
  //       Form.populate(form)
  // }

  // Overload for ScalaTest (no DataTable, accepts varargs)
  def givenIEnterAllTheFields(data: Map[String, String]): Unit = {
    Form.populate(data)
  }

  // ^I am on the '(.*)' page$
  def thenIAmOnThePage(pageName: String): Unit = {
    eventually {
          withClue(s"Fail to be on page: $pageName")(on(pages(pageName)))
        }
  }

  // ^the user-nav header contains a '(.*)' link
  def thenTheUserNavHeaderContainsALink(linkText: String): Unit = {
    val header = Driver.instance.findElement(By.id("proposition-links"))
        header.findElement(By.linkText(linkText)).isDisplayed shouldBe true
  }

  // ^The current page contains link '(.*)' to '(.*)'$
  def thenTheCurrentPageContainsLinkTo(linkText: String, pageName: String): Unit = {
    val href = CurrentPage.linkTextHref(linkText)
        val page = withClue(s"page not found: $pageName")(pages(pageName))
        href.value shouldBe page.url()
  }

  // // ^I see text in fields:$
  // def thenISeeTextInFields(fieldData: DataTable): Unit = {
  //   verifyData(fieldData, _.getText, By.id)
  // }

  // Overload for ScalaTest (no DataTable, accepts varargs)
  def thenISeeTextInFields(data: (String, String)*): Unit = {
    verifyData(data.toMap, _.getText, By.id)
  }

  // // ^I see:$
  // def thenISee(labels: DataTable): Unit = {
  //   val textsToFind: List[String] = TableMisuseAdapters.valuesInColumn(0)(labels)
  //       eventually {
  //         CurrentPage.bodyText() should containInOrder(textsToFind)
  //       }
  // }

  // Overload for ScalaTest (no DataTable, accepts varargs)
  def thenISee(data: (String, String)*): Unit = {
    val textsToFind: List[String] = TableMisuseAdapters.valuesInColumn(0)(data.toMap)
        eventually {
          CurrentPage.bodyText() should containInOrder(textsToFind)
        }
  }

  // // ^I see on current page:$
  // def thenISeeOnCurrentPage(labels: DataTable): Unit = {
  //   val textsToFind = TableMisuseAdapters.valuesInColumn(0)(labels)
  //       Driver.instance.findElement(By.tagName("body")).getText should containInOrder(textsToFind)
  // }

  // Overload for ScalaTest (no DataTable, accepts varargs)
  def thenISeeOnCurrentPage(data: (String, String)*): Unit = {
    val textsToFind = TableMisuseAdapters.valuesInColumn(0)(data.toMap)
        Driver.instance.findElement(By.tagName("body")).getText should containInOrder(textsToFind)
  }

  // ^I click on the '(.*)' link$
  def whenIClickOnTheLink(linkText: String) = {
        val link    = Driver.instance.findElement(By.linkText(linkText))
        val actions = new Actions(Driver.instance)
        actions.moveToElement(link)
        actions.click()
        actions.perform()
  }

  // ^I click on the button with id '(.*)'$
  def whenIClickOnTheButtonWithId(id: String) = {
        val link    = Driver.instance.findElement(By.id(id))
        val actions = new Actions(Driver.instance)
        actions.moveToElement(link)
        actions.click()
        actions.perform()
  }

  // ^I click on the '(.*)' button
  def whenIClickOnTheButton(buttonText: String) = {
    val element = Driver.instance.findElement(By.xpath(s"//button[text()='$buttonText']"))
    val actions = new Actions(Driver.instance)
    actions.moveToElement(element)
    actions.click()
    actions.perform()
  }

  // ^I click on the radio button with id '(.*)'
  def whenIClickOnTheRadioButtonWithId(id: String) = {
        val button = Driver.instance.findElement(By.id(id))
        button.click()
  }

  def verifyData(data: Map[String, String], f: WebElement => String, selector: String => By): Unit = {
    val body                                   = Driver.instance.findElement(By.tagName("body"))
    data.foreach{ 
      case (key, value) =>
        withClue(s"The field '${key}' does not have value: '${value}'") {
          eventually(timeout(1.second)) { f(body.findElement(selector(key))) shouldBe value}
        }
    }
  }

}
