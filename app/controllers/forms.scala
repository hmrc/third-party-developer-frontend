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

package controllers

import domain.ApiSubscriptionFields.SubscriptionField
import domain.{Application, Standard}
import play.api.data.Form
import play.api.data.Forms._

trait ConfirmPassword {
  val password: String
  val confirmPassword: String
}

trait PasswordConfirmation {
  val password: String
}

case class LoginForm(emailaddress: String, password: String)

object LoginForm {

  def invalidCredentials(form: Form[LoginForm], attemptedEmailAddress: String): Form[LoginForm] = {
    form.withError("submissionError", "true")
      .copy(data = Map("emailaddress" -> attemptedEmailAddress))
      .withError("invalidCredentials", FormKeys.invalidCredentialsKey)
      .withGlobalError(FormKeys.invalidCredentialsGlobalKey)
  }

  def accountUnverified(form: Form[LoginForm], email: String) = {
    form.withError("submissionError", "true")
      .withError(FormKeys.emailaddressField, FormKeys.accountUnverifiedKey, controllers.routes.Registration.resendVerification())
      .withGlobalError(FormKeys.accountUnverifiedGlobalKey)
  }

  val form: Form[LoginForm] = Form(
    mapping(
      "emailaddress" -> emailValidator(),
      "password" -> loginPasswordValidator
    )(LoginForm.apply)(LoginForm.unapply)
  )

}


case class PasswordResetForm(password: String, confirmPassword: String) extends ConfirmPassword

object PasswordResetForm {

  def accountUnverified[T](form: Form[T], email: String) = {
    form.withError("submissionError", "true")
      .withError(FormKeys.passwordField, FormKeys.accountUnverifiedKey, controllers.routes.Registration.resendVerification())
      .withGlobalError(FormKeys.accountUnverifiedGlobalKey)
  }

  val form: Form[PasswordResetForm] = Form(
    mapping(
      "password" -> passwordValidator,
      "confirmpassword" -> text
    )(PasswordResetForm.apply)(PasswordResetForm.unapply).verifying(passwordsMatch)
  )

}

case class RegisterForm(firstName: String,
                        lastName: String,
                        emailaddress: String,
                        password: String,
                        confirmPassword: String,
                        organisation: Option[String] = None) extends ConfirmPassword

object RegistrationForm {

  val form: Form[RegisterForm] = Form(
    mapping(
      "firstname" -> firstnameValidator,
      "lastname" -> lastnameValidator,
      "emailaddress" -> emailValidator(),
      "password" -> passwordValidator,
      "confirmpassword" -> text,
      "organisation" -> optional(text)
    )(RegisterForm.apply)(RegisterForm.unapply).verifying(passwordsMatch)
  )

}

final case class DeleteProfileForm(confirmation: Option[String])

object DeleteProfileForm {
  lazy val form = Form(
    mapping(
      "confirmation" -> optional(text).verifying(FormKeys.accountDeleteConfirmationRequiredKey, selection => selection.isDefined)
    )(DeleteProfileForm.apply)(DeleteProfileForm.unapply)
  )
}

case class ProfileForm(firstName: String, lastName: String, organisation: Option[String])

object ProfileForm {
  lazy val form = Form(
    mapping(
      "firstname" -> firstnameValidator,
      "lastname" -> lastnameValidator,
      "organisation" -> optional(text)
    )(ProfileForm.apply)(ProfileForm.unapply)
  )
}

case class ForgotPasswordForm(emailaddress: String)

object ForgotPasswordForm {

  def accountUnverified(form: Form[ForgotPasswordForm], email: String) = {
    form.withError("submissionError", "true")
      .withError(FormKeys.emailaddressField, FormKeys.accountUnverifiedKey, controllers.routes.Registration.resendVerification())
      .withGlobalError(FormKeys.accountUnverifiedGlobalKey)
  }

  val form: Form[ForgotPasswordForm] = Form(
    mapping(
      "emailaddress" -> emailValidator()
    )(ForgotPasswordForm.apply)(ForgotPasswordForm.unapply)
  )

}

case class ChangePasswordForm(currentPassword: String, password: String, confirmPassword: String) extends ConfirmPassword

object ChangePasswordForm {

  def accountUnverified[T](form: Form[T], email: String) = {
    form.withError("submissionError", "true")
      .withError(FormKeys.passwordField, FormKeys.accountUnverifiedKey, controllers.routes.Registration.resendVerification())
      .withGlobalError(FormKeys.accountUnverifiedGlobalKey)
  }

  def invalidCredentials(form: Form[ChangePasswordForm]) = {
    form.withError("submissionError", "true")
      .withError(FormKeys.currentPasswordField, FormKeys.currentPasswordInvalidKey)
      .withGlobalError(FormKeys.currentPasswordInvalidGlobalKey)
  }

  val form: Form[ChangePasswordForm] = Form(
    mapping(
      "currentpassword" -> currentPasswordValidator,
      "password" -> passwordValidator,
      "confirmpassword" -> text
    )(ChangePasswordForm.apply)(ChangePasswordForm.unapply).verifying(passwordsMatch)
  )

}

final case class RemoveTeamMemberForm(email: String)

object RemoveTeamMemberForm {
  val form: Form[RemoveTeamMemberForm] = Form(
    mapping(
      "email" -> emailValidator(FormKeys.teamMemberEmailRequired)
    )(RemoveTeamMemberForm.apply)(RemoveTeamMemberForm.unapply)
  )
}

final case class RemoveTeamMemberConfirmationForm(email: String, confirm: Option[String] = Some(""))

object RemoveTeamMemberConfirmationForm {
  val form: Form[RemoveTeamMemberConfirmationForm] = Form(
    mapping(
      "email" -> emailValidator(FormKeys.teamMemberEmailRequired),
      "confirm" -> optional(text).verifying(FormKeys.removeTeamMemberConfirmNoChoiceKey, s => s.isDefined)
    )(RemoveTeamMemberConfirmationForm.apply)(RemoveTeamMemberConfirmationForm.unapply)
  )
}

case class AddTeamMemberForm(email: String, role: Option[String])

object AddTeamMemberForm {
  def form: Form[AddTeamMemberForm] = Form(
    mapping(
      "email" -> emailValidator(FormKeys.teamMemberEmailRequired),
      "role" -> optional(text).verifying(FormKeys.teamMemberRoleRequired, selection => selection.isDefined)
    )(AddTeamMemberForm.apply)(AddTeamMemberForm.unapply)
  )
}

case class AddApplicationForm(applicationName: String, environment: Option[String], description: Option[String] = None)

object AddApplicationForm {

  val form: Form[AddApplicationForm] = Form(
    mapping(
      "applicationName" -> applicationNameValidator,
      "environment" -> environmentValidator,
      "description" -> optional(text)
    )(AddApplicationForm.apply)(AddApplicationForm.unapply)
  )
}

case class AddApplicationNameForm(applicationName: String)

object AddApplicationNameForm {

  val form: Form[AddApplicationNameForm] = Form(
    mapping(
      "applicationName" -> applicationNameValidator
    )(AddApplicationNameForm.apply)(AddApplicationNameForm.unapply)
  )
}

case class EditApplicationForm(applicationId: String,
                               applicationName: String,
                               description: Option[String] = None,
                               privacyPolicyUrl: Option[String] = None,
                               termsAndConditionsUrl: Option[String] = None)

object EditApplicationForm {

  val form: Form[EditApplicationForm] = Form(
    mapping(
      "applicationId" -> nonEmptyText,
      "applicationName" -> applicationNameValidator,
      "description" -> optional(text),
      "privacyPolicyUrl" -> optional(privacyPolicyUrlValidator),
      "termsAndConditionsUrl" -> optional(tNcUrlValidator)
    )(EditApplicationForm.apply)(EditApplicationForm.unapply)
  )

  def withData(app: Application) = {
    val appAccess = app.access.asInstanceOf[Standard]
    form.fillAndValidate(EditApplicationForm(app.id, app.name, app.description,
      appAccess.privacyPolicyUrl, appAccess.termsAndConditionsUrl))
  }
}

case class SubmitApplicationNameForm(applicationName: String, originalApplicationName: String, password: String = "") extends PasswordConfirmation

object SubmitApplicationNameForm {

  val form: Form[SubmitApplicationNameForm] = Form(
    mapping(
      "applicationName" -> applicationNameValidator,
      "originalApplicationName" -> nonEmptyText,
      "password" -> nonEmptyText
    )(SubmitApplicationNameForm.apply)(SubmitApplicationNameForm.unapply)
  )
}

case class SignOutSurveyForm(rating: Option[Int], improvementSuggestions: String,
                             name: String, email: String, isJavascript: Boolean)

object SignOutSurveyForm {
  val form: Form[SignOutSurveyForm] = Form(
    mapping(
      "rating" -> optional(number(1, 5)),
      "improvementSuggestions" -> text(0, 2000),
      "name" -> text(0, 100),
      "email" -> text(0, 100),
      "isJavascript" -> boolean
    )(SignOutSurveyForm.apply)(SignOutSurveyForm.unapply)
  )
}

case class SupportEnquiryForm(fullname: String, email: String, comments: String)

object SupportEnquiryForm {
  val form: Form[SupportEnquiryForm] = Form(
    mapping(
      "fullname" -> fullnameValidator,
      "emailaddress" -> emailValidator(),
      "comments" -> commentsValidator
    )(SupportEnquiryForm.apply)(SupportEnquiryForm.unapply)
  )
}


final case class DeletePrincipalApplicationForm(deleteConfirm: Option[String] = Some(""))

object DeletePrincipalApplicationForm {

  def form: Form[DeletePrincipalApplicationForm] = Form(
    mapping(
      "deleteConfirm" -> optional(text).verifying(FormKeys.deleteApplicationConfirmNoChoiceKey, s => s.isDefined)
    )(DeletePrincipalApplicationForm.apply)(DeletePrincipalApplicationForm.unapply)
  )
}

final case class SelectClientSecretsToDeleteForm(clientSecretsToDelete: Seq[String])

object SelectClientSecretsToDeleteForm {

  def form: Form[SelectClientSecretsToDeleteForm] = Form(
    mapping(
      "client-secret" -> seq(text)
    )(SelectClientSecretsToDeleteForm.apply)(SelectClientSecretsToDeleteForm.unapply)
  )
}

final case class DeleteClientSecretsConfirmForm(deleteConfirm: Option[String] = Some(""), clientSecretsToDelete: String)

object DeleteClientSecretsConfirmForm {

  def form: Form[DeleteClientSecretsConfirmForm] = Form(
    mapping(
      "deleteConfirm" -> optional(text).verifying(FormKeys.deleteClientSecretsConfirmNoChoiceKey, s => s.isDefined),
      "clientSecretsToDelete" -> text
    )(DeleteClientSecretsConfirmForm.apply)(DeleteClientSecretsConfirmForm.unapply)
  )
}

final case class VerifyPasswordForm(password: String)

object VerifyPasswordForm {

  val form: Form[VerifyPasswordForm] = Form(
    mapping(
      "password" -> loginPasswordValidator
    )(VerifyPasswordForm.apply)(VerifyPasswordForm.unapply)
  )
}

final case class SubscriptionConfirmationForm(subscribeConfirm: Option[String] = Some(""))

object SubscriptionConfirmationForm {

  def form: Form[SubscriptionConfirmationForm] = Form(
    mapping(
      "subscribeConfirm" -> optional(text).verifying(FormKeys.subscriptionConfirmationNoChoiceKey, s => s.isDefined)
    )(SubscriptionConfirmationForm.apply)(SubscriptionConfirmationForm.unapply)
  )
}

final case class UnsubscribeConfirmationForm(unsubscribeConfirm: Option[String] = Some(""))

object UnsubscribeConfirmationForm {

  def form: Form[UnsubscribeConfirmationForm] = Form(
    mapping(
      "unsubscribeConfirm" -> optional(text).verifying(FormKeys.unsubscribeConfirmationNoChoiceKey, s => s.isDefined)
    )(UnsubscribeConfirmationForm.apply)(UnsubscribeConfirmationForm.unapply)
  )
}

final case class ChangeSubscriptionForm(subscribed: Option[Boolean])

object ChangeSubscriptionForm {
  def form: Form[ChangeSubscriptionForm] = Form(
    mapping(
      "subscribed" -> optional(boolean).verifying(FormKeys.changeSubscriptionNoChoiceKey, _.isDefined)
    )(ChangeSubscriptionForm.apply)(ChangeSubscriptionForm.unapply))
}

final case class ChangeSubscriptionConfirmationForm(subscribed: Boolean, confirm: Option[Boolean])

object ChangeSubscriptionConfirmationForm {
  def form: Form[ChangeSubscriptionConfirmationForm] = Form(
    mapping(
      "subscribed" -> boolean,
      "confirm" -> optional(boolean).verifying(FormKeys.subscriptionConfirmationNoChoiceKey, _.isDefined)
    )(ChangeSubscriptionConfirmationForm.apply)(ChangeSubscriptionConfirmationForm.unapply))
}

case class SubscriptionFieldsForm(fields: Seq[SubscriptionField])

object SubscriptionFieldsForm {
  val form = Form(
    mapping(
      "fields" -> seq(
        mapping(
          "name" -> text,
          "description" -> text,
          "hint" -> text,
          "type" -> text,
          "value" -> optional(text))(SubscriptionField.apply)(SubscriptionField.unapply))
    )(SubscriptionFieldsForm.apply)(SubscriptionFieldsForm.unapply)
  )
}

final case class AddRedirectForm(redirectUri: String)

object AddRedirectForm {
  val form = Form(
    mapping("redirectUri" -> redirectUriValidator)(AddRedirectForm.apply)(AddRedirectForm.unapply)
  )
}

final case class DeleteRedirectForm(redirectUri: String)

object DeleteRedirectForm {
  val form = Form(
    mapping("redirectUri" -> text)(DeleteRedirectForm.apply)(DeleteRedirectForm.unapply)
  )
}

final case class DeleteRedirectConfirmationForm(redirectUri: String, deleteRedirectConfirm: Option[String] = Some(""))

object DeleteRedirectConfirmationForm {

  def form: Form[DeleteRedirectConfirmationForm] = Form(
    mapping(
      "redirectUri" -> text,
      "deleteRedirectConfirm" -> optional(text).verifying(FormKeys.deleteRedirectConfirmationNoChoiceKey, s => s.isDefined)
    )(DeleteRedirectConfirmationForm.apply)(DeleteRedirectConfirmationForm.unapply)
  )
}

final case class ChangeRedirectForm(originalRedirectUri: String, newRedirectUri: String)

object ChangeRedirectForm {
  val form = Form(
    mapping(
      "originalRedirectUri" -> text,
      "newRedirectUri" -> redirectUriValidator
    )(ChangeRedirectForm.apply)(ChangeRedirectForm.unapply)
  )
}

final case class Remove2SVConfirmForm(removeConfirm: Option[String] = Some(""))

object Remove2SVConfirmForm {

  def form: Form[Remove2SVConfirmForm] = Form(
    mapping(
      "removeConfirm" -> optional(text).verifying(FormKeys.remove2SVConfirmNoChoiceKey, s => s.isDefined)
    )(Remove2SVConfirmForm.apply)(Remove2SVConfirmForm.unapply)
  )
}

final case class ChangeIpWhitelistForm(description: String)

object ChangeIpWhitelistForm {
  val form: Form[ChangeIpWhitelistForm] = Form(
    mapping("description" -> whitelistedIpsValidator)(ChangeIpWhitelistForm.apply)(ChangeIpWhitelistForm.unapply)
  )
}
