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

case class SignInPage(override val pageHeading: String = "Sign in") extends FormPage {
  override val url: String = s"${Env.host}/developer/login"
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

case class ResetPasswordLink(email: String, code: String) extends WebLink {
  override val url: String = s"${Env.host}/developer/$email/reset-password?code=$code"
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


