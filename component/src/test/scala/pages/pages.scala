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

package pages

import org.openqa.selenium.By
import steps.EnvConfig
import utils.MfaData

import uk.gov.hmrc.selenium.webdriver.Driver

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaType

trait FormPage extends WebPage {
  def dataError(name: String) = dataAttribute(s"[data-$name]")

  def fieldError(fieldName: String) = dataAttribute(s"[data-field-error-$fieldName]")

  def globalError(fieldName: String) = dataAttribute(s"[data-global-error-$fieldName]")

  private def dataAttribute(className: String) = findElement(By.cssSelector(className)).get

  def globalErrors = findElements(By.cssSelector("[data-global-error]"))

  def hasElementWithId(id: String) = findElements(By.id(id)).size == 1
}

trait SubmitButton {
  self: FormPage =>

  protected val submitButton: By = By.id("submit")
}

object CurrentPage extends FormPage {
  override val url: String = ""
  override val pageHeading = ""

  def linkTextHref(linkText: String) = {
    val link = findElement(By.linkText(linkText))
    link.map(_.getAttribute("href"))
  }
}

object RegistrationPage extends FormPage {
  override val url: String = s"${EnvConfig.host}/developer/registration"
  override val pageHeading = "Register for a developer account"
}

object ResendConfirmationPage extends FormPage {
  override val url: String = s"${EnvConfig.host}/developer/resend-confirmation"
  override val pageHeading = "Resend confirmation email"
}

object EmailConfirmationPage extends FormPage {
  override val url: String = s"${EnvConfig.host}/developer/confirmation"
  override val pageHeading = "Confirm your email address"
}

object EmailPreferencesSummaryPage extends FormPage {
  override val url: String = s"${EnvConfig.host}/developer/profile/email-preferences"
  override val pageHeading = "Manage your Developer Hub email preferences"
}

object ManageProfilePage extends FormPage {
  override val pageHeading = "Manage profile"
  override val url: String = s"${EnvConfig.host}/developer/profile"
}

object ChangeProfileDetailsPage extends FormPage {
  override val pageHeading = "Change profile details"
  override val url: String = s"${EnvConfig.host}/developer/profile/change"
}

object ChangePasswordSuccessPage extends FormPage {
  override val pageHeading = "Password changed"
  override val url: String = s"${EnvConfig.host}/developer/profile/password"
}

object ManageApplicationPage extends FormPage {
  override val pageHeading = "View all applications"
  override val url: String = s"${EnvConfig.host}/developer/applications/"
}

object AddApplicationEmptyPage extends FormPage {
  override val pageHeading = "Start using our REST APIs"
  override val url: String = s"${EnvConfig.host}/developer/no-applications-start/"
}

object NoApplicationsPage extends FormPage {
  override val pageHeading = "Using the Developer Hub"
  override val url: String = s"${EnvConfig.host}/developer/no-applications"
}

object AddApplicationSuccessPage extends FormPage {
  override val pageHeading = "You added"
  override val url: String = s"${EnvConfig.host}/developer/applications/add"

  override def isCurrentPage(): Boolean = heading().startsWith(pageHeading)
}

case object AccountDeletionConfirmationPage extends FormPage {
  override val pageHeading: String = "Delete account"
  override val url: String         = s"${EnvConfig.host}/developer/profile/delete"
}

case object AccountDeletionRequestSubmittedPage extends FormPage {
  override val pageHeading: String = "Request submitted"
  override val url: String         = s"${EnvConfig.host}/developer/profile/delete"
}

class SignInPage private (override val pageHeading: String) extends FormPage with SubmitButton {
  override val url: String = s"${EnvConfig.host}/developer/login"

  private val emailField    = By.id("emailaddress")
  private val passwordField = By.id("password")

  def signInWith(email: String, password: String): Unit = {
    Driver.instance.manage().deleteAllCookies()
    Driver.instance.navigate().refresh()

    sendKeys(emailField, email)
    sendKeys(passwordField, password)
    click(submitButton)
  }
}

case object SignInPage {
  val default       = new SignInPage("Sign in")
  val passwordReset = new SignInPage("You have reset your password")
}

case object SelectMfaPage extends FormPage {
  override val pageHeading: String = "How do you want to get access codes?"
  override val url: String         = s"${EnvConfig.host}/developer/login/select-mfa"
}

case object RecommendMfaPage extends FormPage {
  override val pageHeading: String = "Add 2-step verification"
  override val url: String         = s"${EnvConfig.host}/developer/login/2sv-recommendation"

  private val skipButton = By.id("skip")

  def skip2SVReminder(): Unit = click(skipButton)
}

case object RecommendMfaSkipAcknowledgePage extends FormPage {
  override val pageHeading: String = "Add 2-step verification"
  override val url: String         = s"${EnvConfig.host}/developer/login/2SV-not-set"

  private val submitButton = By.id("submit")

  def confirmSkip2SV(): Unit = click(submitButton)
}

case object AuthAppLoginAccessCodePage extends MfaData {
  val page = LoginAccessCodePage(authAppMfaId.value.toString, MfaType.AUTHENTICATOR_APP, "Enter your access code")
}

case class LoginAccessCodePage(mfaId: String, mfaType: MfaType, headingVal: String) extends FormPage with SubmitButton {
  override val pageHeading: String = headingVal
  override val url: String         = s"${EnvConfig.host}/developer/login-mfa?mfaId=${mfaId}&mfaType=${mfaType.toString}"

  private val accessCodeField    = By.name("accessCode")
  private val rememberMeCheckbox = By.name("rememberMe")

  def enterAccessCode(accessCode: String, rememberMe: Boolean = false) = {
    sendKeys(accessCodeField, accessCode)
    if (rememberMe)
      selectCheckbox(rememberMeCheckbox)
    else
      deselectCheckbox(rememberMeCheckbox)
    click(submitButton)
  }
}

case object AuthAppSetupReminderPage extends FormPage {
  override val pageHeading: String = "Get access codes by an authenticator app"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/auth-app/setup/reminder"
}

case object AuthAppStartPage extends FormPage {
  override val pageHeading: String = "You need an authenticator app on your device"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/auth-app/start"
}

case object SmsLoginAccessCodePage extends MfaData {
  val page = LoginAccessCodePage(smsMfaId.value.toString, MfaType.SMS, "Enter the access code")
}

case object SmsSetupReminderPage extends FormPage {
  override val pageHeading: String = "Get access codes by text"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/sms/setup/reminder"
}

case object SmsSetupSkippedPage extends FormPage {
  override val pageHeading: String = "Get access codes by text later"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/sms/setup/skip"
}

case object MfaRemovalCompletePage extends FormPage {
  override val pageHeading: String = "You've removed this security preference"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/remove-mfa/complete"
}

case object Setup2svQrPage extends FormPage {
  override val pageHeading: String = "Set up your authenticator app"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/auth-app/setup"
}

case object AuthenticatorAppAccessCodePage extends FormPage with SubmitButton {

  override val pageHeading: String = "Enter your access code"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/auth-app/access-code"

  private val accessCodeField = By.name("accessCode")

  def enterAccessCode(accessCode: String) = {
    sendKeys(accessCodeField, accessCode)
    click(submitButton)
  }
}

case object SmsAccessCodePage extends FormPage with SubmitButton {

  override val pageHeading: String = "Enter the access code"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/sms/access-code"

  private val accessCodeField = By.name("accessCode")

  def enterAccessCode(accessCode: String) = {
    sendKeys(accessCodeField, accessCode)
    click(submitButton)
  }
}

case object CreateNameForAuthAppPage extends FormPage {

  private val nameField = By.name("name")

  def enterName(name: String) = sendKeys(nameField, name)

  override val pageHeading: String = "Create a name for your authenticator app"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/auth-app/name"
}

case object SmsMobileNumberPage extends FormPage with SubmitButton {

  override val pageHeading: String = "Enter a mobile phone number"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/sms/setup"

  private val mobileNumberField = By.name("mobileNumber")

  def enterMobileNumber(mobileNumber: String) = {
    sendKeys(mobileNumberField, mobileNumber)
    click(submitButton)
  }
}

case object SecurityPreferencesPage extends FormPage {
  override val pageHeading: String = "Your security preferences"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences"
}

case object AuthenticatorAppSetupSkippedPage extends FormPage {
  override val pageHeading: String = "Get access codes by authenticator app later"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/auth-app/setup/skip"
}

case object AuthenticatorAppSetupCompletePage extends FormPage {
  override val pageHeading: String = "You can now get access codes by authenticator app"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/auth-app/setup/complete"
}

case object SmsSetupCompletePage extends FormPage {
  override val pageHeading: String = "You can now get access codes by text"
  override val url: String         = s"${EnvConfig.host}/developer/profile/security-preferences/sms/setup/complete"
}

case object PasswordResetConfirmationPage extends FormPage {
  override val pageHeading: String = "Password reset email sent"
  override val url: String         = s"${EnvConfig.host}/developer/developer/forgot-password"
}

case class ResetPasswordPage(code: String) extends FormPage {
  override val pageHeading: String = "Create a new password"
  override val url: String         = s"${EnvConfig.host}/developer/reset-password"
}

object YouHaveResetYourPasswordPage extends FormPage {
  override val pageHeading = "You have reset your password"
  override val url: String = s"${EnvConfig.host}/developer/reset-password"
}

object ResetPasswordLinkNoLongerValidPage extends FormPage {
  override val pageHeading = "Reset password link no longer valid"
  override val url: String = s"${EnvConfig.host}/developer/reset-password/error"
}

case object SignOutSurveyPage extends FormPage {
  override val pageHeading = "Are you sure you want to sign out?"
  override val url: String = s"${EnvConfig.host}/developer/logout/survey"
}

case object SignOutPage extends WebLink {
  override val url: String = s"${EnvConfig.host}/developer/logout"
}

case class ResendVerificationLink(email: LaxEmailAddress) extends WebLink {
  override val url: String = s"${EnvConfig.host}/developer/resend-verification"
}

case class VerificationLink(verificationCode: String) extends WebLink {
  override val url: String = s"${EnvConfig.host}/developer/verification?code=$verificationCode"
}

case class VerificationPage(verificationCode: String) extends FormPage {
  override val pageHeading: String = "Email address verified"
  override val url: String         = s"${EnvConfig.host}/developer/verification?code=$verificationCode"
}

case class SubscriptionLink(id: String) extends WebLink {
  override val url: String = s"${EnvConfig.host}/developer/applications/$id/subscriptions"
}

case class SubscriptionPage(id: String) extends FormPage {
  override val pageHeading = "Manage API subscriptions"
  override val url: String = s"${EnvConfig.host}/developer/applications/$id/subscriptions"
}

case class DeleteApplicationPage(id: String) extends FormPage {
  override val pageHeading = "Delete application"
  override val url: String = s"${EnvConfig.host}/developer/applications/$id/delete"
}

case class DeleteApplicationConfirmPage(id: String) extends FormPage {
  override val pageHeading = "Delete application"
  override val url: String = s"${EnvConfig.host}/developer/applications/$id/delete-confirm"
}

case class DeleteApplicationCompletePage(id: String) extends FormPage {
  override val pageHeading = "Request submitted"
  override val url: String = s"${EnvConfig.host}/developer/applications/$id/delete"
}
