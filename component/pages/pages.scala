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

package pages

import org.openqa.selenium.By
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import steps.{Env, Form}

trait FormPage extends WebPage with ApplicationLogger {
  val pageHeading: String

  override def isCurrentPage: Boolean = find(tagName("h1")).fold(false)({
    e =>
      logger.info(s"HEADING: ${e.text}")
      e.text == pageHeading
  })

  def dataError(name: String) = dataAttribute(s"[data-$name]")

  def fieldError(fieldName: String) = dataAttribute(s"[data-field-error-$fieldName]")

  def globalError(fieldName: String) = dataAttribute(s"[data-global-error-$fieldName]")

  def validateLoggedInAs(userFullName: String) = {
    val header = webDriver.findElement(By.id("user-nav-links"))
    header.findElement(By.linkText(userFullName)).isDisplayed shouldBe true
    header.findElement(By.linkText("Sign out")).isDisplayed shouldBe true
    header.findElements(By.linkText("Sign in")) shouldBe empty
  }

  private def dataAttribute(className: String) = webDriver.findElement(By.cssSelector(className))

  def globalErrors = webDriver.findElements(By.cssSelector("[data-global-error]"))

  def hasElementWithId(id: String) = webDriver.findElements(By.id(id)).size() == 1
}

object CurrentPage extends FormPage {
  override val url: String = ""
  override val pageHeading = ""
}

object RegistrationPage extends FormPage {
  override val url: String = s"${Env.host}/developer/registration"
  override val pageHeading = "Register for a developer account"
}

object ResendConfirmationPage extends FormPage {
  override val url: String = s"${Env.host}/developer/resend-confirmation"
  override val pageHeading = "Resend confirmation email"
}

object EmailConfirmationPage extends FormPage {
  override val url: String = s"${Env.host}/developer/confirmation"
  override val pageHeading = "Confirm your email address"
}

object EmailPreferencesSummaryPage extends FormPage {
  override val url: String = s"${Env.host}/developer/profile/email-preferences"
  override val pageHeading = "Email preferences"
}

object ManageProfilePage extends FormPage {
  override val pageHeading = "Manage profile"
  override val url: String = s"${Env.host}/developer/profile"
}

object ChangeProfileDetailsPage extends FormPage {
  override val pageHeading = "Change profile details"
  override val url: String = s"${Env.host}/developer/profile/change"
}

object ChangePasswordSuccessPage extends FormPage {
  override val pageHeading = "Password changed"
  override val url: String = s"${Env.host}/developer/profile/password"
}

object ManageApplicationPage extends FormPage {
  override val pageHeading = "View all applications"
  override val url: String = s"${Env.host}/developer/applications/"
}

object AddApplicationEmptyPage extends FormPage {
  override val pageHeading = "Start using our REST APIs"
  override val url: String = s"${Env.host}/developer/no-applications-start/"
}

object NoApplicationsPage extends FormPage {
  override val pageHeading = "Using the Developer Hub"
  override val url: String = s"${Env.host}/developer/no-applications"
}

object AddApplicationSuccessPage extends FormPage {
  override val pageHeading = "You added"
  override val url: String = s"${Env.host}/developer/applications/add"

  override def isCurrentPage: Boolean = find(tagName("h1")).fold(false)({
    e =>
      logger.info(s"HEADING: ${e.text}")
      e.text.startsWith(pageHeading)
  })
}

case object AccountDeletionConfirmationPage extends FormPage {
  override val pageHeading: String = "Delete account"
  override val url: String = s"${Env.host}/developer/profile/delete"
}

case object AccountDeletionRequestSubmittedPage extends FormPage {
  override val pageHeading: String = "Request submitted"
  override val url: String = s"${Env.host}/developer/profile/delete"
}

case class SignInPage(override val pageHeading: String = "Sign in") extends FormPage {
  override val url: String = s"${Env.host}/developer/login"
}

case object RecommendMfaPage extends FormPage {
  override val pageHeading: String = "Add 2-step verification"
  override val url: String = s"${Env.host}/developer/login/2sv-recommendation"
}

case object RecommendMfaSkipAcknowledgePage extends FormPage {
  override val pageHeading: String = "Add 2-step verification"
  override val url: String = s"${Env.host}/developer/login/2SV-not-set"
}

case object Login2svEnterAccessCodePage extends FormPage {
  def clickContinue() = {
    click on id("submit")
  }

  override val pageHeading: String = "Enter your access code"
  override val url: String = s"${Env.host}/developer/login-totp"

  def enterAccessCode(accessCode: String, rememberMe: Boolean = false) = {
    val formData = Map("accessCode" -> accessCode, "rememberMe" -> s"$rememberMe")

    Form.populate(formData)
  }
}

case object AuthAppStartPage extends FormPage {
  override val pageHeading: String = "You need an authenticator app on your device"
  override val url: String = s"${Env.host}/developer/profile/security-preferences/auth-app/start"
}

case object AuthAppAccessCodePage extends FormPage {
  def clickContinue() = {
    click on id("submit")
  }

  override val pageHeading: String = "Enter your access code"
  override val url: String = s"${Env.host}/developer/profile/security-preferences/auth-app/access-code"

  def enterAccessCode(accessCode: String) = {
    val formData = Map("accessCode" -> accessCode)

    Form.populate(formData)
  }
}

case object MfaRemovalCompletePage extends FormPage {
  override val pageHeading: String = "You've removed this security preference"
  override val url: String = s"${Env.host}/developer/profile/security-preferences/remove-mfa/complete"
}

case object Setup2svQrPage extends FormPage {
  override val pageHeading: String = "Set up your authenticator app"
  override val url: String = s"${Env.host}/developer/profile/security-preferences/auth-app/setup"
}

case object Setup2svEnterAccessCodePage extends FormPage {
  def clickContinue() = {
    click on id("submit")
  }

  override val pageHeading: String = "Enter your access code"
  override val url: String = s"${Env.host}/developer/profile/security-preferences/auth-app/access-code"

  def enterAccessCode(accessCode: String) = {
    val formData = Map("accessCode" -> accessCode)

    Form.populate(formData)
  }
}

case object CreateNameForAuthAppPage extends FormPage {
  def enterName(name: String) = Form.populate(Map("name" -> name))(webDriver)

  override val pageHeading: String = "Create a name for your authenticator app"
  override val url: String = s"${Env.host}/developer/profile/security-preferences/auth-app/name"
}

case object SecurityPreferencesPage extends FormPage {
  override val pageHeading: String = "Your security preferences"
  override val url: String = s"${Env.host}/developer/profile/security-preferences"
}

case object AuthenticatorAppSetupCompletePage extends FormPage {
  override val pageHeading: String = "You can now get access codes by authenticator app"
  override val url: String = s"${Env.host}/developer/profile/security-preferences/auth-app/setup/complete"
}

case object PasswordResetConfirmationPage extends FormPage {
  override val pageHeading: String = "Password reset email sent"
  override val url: String = s"${Env.host}/developer/developer/forgot-password"
}

case class ResetPasswordPage(code: String) extends FormPage {
  override val pageHeading: String = "Create a new password"
  override val url: String = s"${Env.host}/developer/reset-password"
}

object YouHaveResetYourPasswordPage extends FormPage {
  override val pageHeading = "You have reset your password"
  override val url: String = s"${Env.host}/developer/reset-password"
}

object ResetPasswordLinkNoLongerValidPage extends FormPage {
  override val pageHeading = "Reset password link no longer valid"
  override val url: String = s"${Env.host}/developer/reset-password/error"
}

case object SignOutSurveyPage extends FormPage {
  override val pageHeading = "Are you sure you want to sign out?"
  override val url: String = s"${Env.host}/developer/logout/survey"
}

object SignInPage {
  val default = SignInPage("Sign in")
  val passwordReset = SignInPage("You have reset your password")
}

case object SignOutPage extends WebLink {
  override val url: String = s"${Env.host}/developer/logout"
}

case class ResendVerificationLink(email: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/resend-verification"
}

case class VerificationLink(verificationCode: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/verification?code=$verificationCode"
}

case class VerificationPage(verificationCode: String) extends FormPage {
  override val pageHeading: String = "Email address verified"
  override val url: String = s"${Env.host}/developer/verification?code=$verificationCode"
}

case class SubscriptionLink(id: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/applications/$id/subscriptions"
}

case class SubscriptionPage(id: String) extends FormPage {
  override val pageHeading = "Manage API subscriptions"
  override val url: String = s"${Env.host}/developer/applications/$id/subscriptions"
}

case class DeleteApplicationPage(id: String) extends FormPage {
  override val pageHeading = "Delete application"
  override val url: String = s"${Env.host}/developer/applications/$id/delete"
}

case class DeleteApplicationConfirmPage(id: String) extends FormPage {
  override val pageHeading = "Delete application"
  override val url: String = s"${Env.host}/developer/applications/$id/delete-confirm"
}

case class DeleteApplicationCompletePage(id: String) extends FormPage {
  override val pageHeading = "Request submitted"
  override val url: String = s"${Env.host}/developer/applications/$id/delete"
}


