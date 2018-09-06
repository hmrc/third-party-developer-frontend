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

package component.pages

import component.steps.Env
import org.openqa.selenium.By
import play.api.Logger

trait FormPage extends WebPage {
  val pageHeading: String

  override def isCurrentPage: Boolean = find(tagName("h1")).fold(false)({
    e => Logger.info(s"HEADING: ${e.text}")
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

  def fieldLink = webDriver.findElements(By.cssSelector("[data-link-error-emailaddress]"))
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

object ErrorPage extends FormPage {
  override val pageHeading = "Sorry, we’re experiencing technical difficulties"
  override val url: String = s"${Env.host}/developer/registration"
}

object BadRequestPage extends FormPage {
  override val pageHeading = "Invalid verification code"
  override val url: String = s"${Env.host}/developer/verification?code=verificationCode"
}

object ForgotPasswordPage extends FormPage {
  override val pageHeading = "Reset your password"
  override val url: String = s"${Env.host}/developer/forgot-password"
}

object ResetPasswordPage extends FormPage {
  override val pageHeading = "Create a new password"
  override val url: String = s"${Env.host}/developer/reset-password"
}

object ChangePasswordPage extends FormPage {
  override val pageHeading = "Your password has expired"
  override val url: String = s"${Env.host}/developer/change-password"
}

object ManageProfilePage extends FormPage {
  override val pageHeading = "Manage profile"
  override val url: String = s"${Env.host}/developer/profile"
}

object ChangeProfileDetailsPage extends FormPage {
  override val pageHeading = "Change profile details"
  override val url: String = s"${Env.host}/developer/profile/change"
}

object EditPasswordPage extends FormPage {
  override val pageHeading = "Change password"
  override val url: String = s"${Env.host}/developer/profile/password"
}

object ManageProfileSuccessPage extends FormPage {
  override val pageHeading = "You have updated your profile"
  override val url: String = s"${Env.host}/developer/profile"
}

object ChangePasswordSuccessPage extends FormPage {
  override val pageHeading = "Password changed"
  override val url: String = s"${Env.host}/developer/profile/password"
}

object ManageApplicationPage extends FormPage {
  override val pageHeading = "View all applications"
  override val url: String = s"${Env.host}/developer/applications/"
}
object ManageApplicationEmptyPage extends FormPage {
  override val pageHeading = "Welcome to your account"
  override val url: String = s"${Env.host}/developer/applications/"
}

object AddApplicationPage extends FormPage {
  override val pageHeading = "Add an application"
  override val url: String = s"${Env.host}/developer/applications/add"
}

object AddApplicationSuccessPage extends FormPage {
  override val pageHeading = "Application added"
  override val url: String = s"${Env.host}/developer/applications/add"
}

case object AccountDeletionConfirmationPage extends FormPage {
  override val pageHeading: String = "Delete account"
  override val url: String = s"${Env.host}/developer/profile/delete"
}

case object AccountDeletionRequestSubmittedPage extends FormPage {
  override val pageHeading: String = "Request submitted"
  override val url: String = s"${Env.host}/developer/profile/delete"
}

case class EditApplicationPage(id: String) extends FormPage {
  override val pageHeading = "Manage details"
  override val url: String = s"${Env.host}/developer/applications/$id/details"
}

case class PrivilegedOrROPCApplicationPage(id: String, name: String = "App name") extends FormPage {
  override val pageHeading = name
  override val url: String = s"${Env.host}/developer/applications/$id/details"
}

case class UnsubscribeAPIConfirmationPage(appId: String) extends FormPage {
  override val pageHeading = "Unsubscribe from an API"
  override val url: String = s"${Env.host}/developer/applications/$appId/unsubscribe"
}

case class CheckEmailPage(email: String) extends FormPage {
  override val pageHeading = "Password reset email sent"
  override val url: String = s"${Env.host}/developer/"
}

case class SubmitApplicationForCheckPage(applicationId: String) extends FormPage {
  override val pageHeading = "Submit your application for checking"
  override val url: String = s"${Env.host}/developer/applications/$applicationId/request-check"
}

case class NameSubmittedForCheckPage(applicationId: String) extends FormPage {
  override val pageHeading = "Name submitted for checking"
  override val url: String = s"${Env.host}/developer/applications/$applicationId/request-check"
}

case class ResetPasswordCodeErrorPage(emailAddress: String, invalidCode: String) extends FormPage {
  override val pageHeading = "Reset password link no longer valid"
  override val url: String = s"${Env.host}/developer/$emailAddress/reset-password?code=$invalidCode"
}

case class InvalidVerificationCodeErrorPage(invalidCode: String) extends FormPage {
  override val pageHeading = "Invalid verification code"
  override val url: String = s"${Env.host}/developer/verification?code=$invalidCode"
}

case class SignInPage(override val pageHeading: String = "Sign in") extends FormPage {
  override val url: String = s"${Env.host}/developer/login"
}

object AccountLockedPage extends FormPage {
  override val pageHeading = "Account locked"
  override val url: String = s"${Env.host}/developer/locked"
}

case object SignOutSurveyPage extends FormPage {
  override val pageHeading = "Give feedback"
  override val url: String = s"${Env.host}/developer/logout/survey"
}

object SignInPage {
  val default = SignInPage("Sign in")
  val passwordReset = SignInPage("You have reset your password")
}

case object SignOutPage extends WebLink {
  override val url: String = s"${Env.host}/developer/logout"
}

case class ResetPasswordLink(email: String, code: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/$email/reset-password?code=$code"
}

case class ApplicationVerificationLink(verificationCode: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/application-verification?code=$verificationCode"
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

case class EditApplicationLink(id: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/applications/$id"
}

case class CredentialsLink(id: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/applications/$id/credentials"
}

case class CredentialsPage(id: String) extends FormPage {
  override val pageHeading = "Manage credentials"
  override val url: String = s"${Env.host}/developer/applications/$id/credentials"
}

case class ManageTeamLink(id: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/applications/$id/team-members"
}

case class ManageTeamPage(id: String) extends FormPage {
  override val pageHeading = "Manage team members"
  override val url: String = s"${Env.host}/developer/applications/$id/team-members"
}

case class SubscriptionLink(id: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/applications/$id/subscriptions"
}

case class SubscriptionPage(id: String) extends FormPage {
  override val pageHeading = "Manage API subscriptions"
  override val url: String = s"${Env.host}/developer/applications/$id/subscriptions"
}

case class DetailsLink(id: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/applications/$id/details"
}

case class DetailsPage(id: String) extends FormPage {
  override val pageHeading = "Manage details"
  override val url: String = s"${Env.host}/developer/applications/$id/details"
}

case class DeleteApplicationLink(id: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/applications/$id/delete"
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


