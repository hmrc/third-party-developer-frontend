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

import domain.models.applications.{Application, ApplicationId, Standard}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

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
    form
      .withError("submissionError", "true")
      .copy(data = Map("emailaddress" -> attemptedEmailAddress))
      .withError("invalidCredentials", FormKeys.invalidCredentialsKey)
      .withGlobalError(FormKeys.invalidCredentialsGlobalKey)
  }

  def accountUnverified(form: Form[LoginForm], email: String) = {
    form
      .withError("submissionError", "true")
      .withError(
        FormKeys.emailaddressField,
        FormKeys.accountUnverifiedKey,
        controllers.routes.Registration.resendVerification()
      )
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
    form
      .withError("submissionError", "true")
      .withError(
        FormKeys.passwordField,
        FormKeys.accountUnverifiedKey,
        controllers.routes.Registration.resendVerification()
      )
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
      "confirmation" -> optional(text)
        .verifying(FormKeys.accountDeleteConfirmationRequiredKey, selection => selection.isDefined)
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
    form
      .withError("submissionError", "true")
      .withError(
        FormKeys.emailaddressField,
        FormKeys.accountUnverifiedKey,
        controllers.routes.Registration.resendVerification()
      )
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
    form
      .withError("submissionError", "true")
      .withError(
        FormKeys.passwordField,
        FormKeys.accountUnverifiedKey,
        controllers.routes.Registration.resendVerification()
      )
      .withGlobalError(FormKeys.accountUnverifiedGlobalKey)
  }

  def invalidCredentials(form: Form[ChangePasswordForm]) = {
    form
      .withError("submissionError", "true")
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

final case class RemoveTeamMemberConfirmationForm(email: String, confirm: Option[String] = Some(""))

object RemoveTeamMemberConfirmationForm {
  val form: Form[RemoveTeamMemberConfirmationForm] = Form(
    mapping(
      "email" -> emailValidator(FormKeys.teamMemberEmailRequired),
      "confirm" -> optional(text)
        .verifying(FormKeys.removeTeamMemberConfirmNoChoiceKey, s => s.isDefined)
    )(RemoveTeamMemberConfirmationForm.apply)(RemoveTeamMemberConfirmationForm.unapply)
  )
}

final case class RemoveTeamMemberCheckPageConfirmationForm(email: String)

object RemoveTeamMemberCheckPageConfirmationForm {
  val form: Form[RemoveTeamMemberCheckPageConfirmationForm] = Form(
    mapping(
      "email" -> emailValidator(FormKeys.teamMemberEmailRequired)
    )(RemoveTeamMemberCheckPageConfirmationForm.apply)(
      RemoveTeamMemberCheckPageConfirmationForm.unapply
    )
  )
}

case class AddTeamMemberForm(email: String, role: Option[String])

object AddTeamMemberForm {
  def form: Form[AddTeamMemberForm] = Form(
    mapping(
      "email" -> emailValidator(FormKeys.teamMemberEmailRequired),
      "role" -> optional(text)
        .verifying(FormKeys.teamMemberRoleRequired, selection => selection.isDefined)
    )(AddTeamMemberForm.apply)(AddTeamMemberForm.unapply)
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

case class EditApplicationForm(applicationId: ApplicationId,
                                applicationName: String,
                                description: Option[String] = None,
                                privacyPolicyUrl: Option[String] = None,
                                termsAndConditionsUrl: Option[String] = None)

object EditApplicationForm {

  val form: Form[EditApplicationForm] = Form(
    mapping(
      "applicationId" -> nonEmptyText.transform[ApplicationId](ApplicationId(_), id => id.value),
      "applicationName" -> applicationNameValidator,
      "description" -> optional(text),
      "privacyPolicyUrl" -> optional(privacyPolicyUrlValidator),
      "termsAndConditionsUrl" -> optional(tNcUrlValidator)
    )(EditApplicationForm.apply)(EditApplicationForm.unapply)
  )

  def withData(app: Application) = {
    val appAccess = app.access.asInstanceOf[Standard]
    form.fillAndValidate(
      EditApplicationForm(
        app.id,
        app.name,
        app.description,
        appAccess.privacyPolicyUrl,
        appAccess.termsAndConditionsUrl
      )
    )
  }
}

case class SubmitApplicationNameForm(applicationName: String,
                                      originalApplicationName: String,
                                      password: String = "") extends PasswordConfirmation

object SubmitApplicationNameForm {

  val form: Form[SubmitApplicationNameForm] = Form(
    mapping(
      "applicationName" -> applicationNameValidator,
      "originalApplicationName" -> nonEmptyText,
      "password" -> nonEmptyText
    )(SubmitApplicationNameForm.apply)(SubmitApplicationNameForm.unapply)
  )
}

case class SignOutSurveyForm(rating: Option[Int],
                              improvementSuggestions: String,
                              name: String,
                              email: String,
                              isJavascript: Boolean)

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
      "deleteConfirm" -> optional(text)
        .verifying(FormKeys.deleteApplicationConfirmNoChoiceKey, s => s.isDefined)
    )(DeletePrincipalApplicationForm.apply)(DeletePrincipalApplicationForm.unapply)
  )
}

final case class ChangeSubscriptionForm(subscribed: Option[Boolean])

object ChangeSubscriptionForm {
  def form: Form[ChangeSubscriptionForm] =
    Form(
      mapping(
        "subscribed" -> optional(boolean)
          .verifying(FormKeys.changeSubscriptionNoChoiceKey, _.isDefined)
      )(ChangeSubscriptionForm.apply)(ChangeSubscriptionForm.unapply)
    )
}

final case class ChangeSubscriptionConfirmationForm(subscribed: Boolean, confirm: Option[Boolean])

object ChangeSubscriptionConfirmationForm {
  def form: Form[ChangeSubscriptionConfirmationForm] =
    Form(
      mapping(
        "subscribed" -> boolean,
        "confirm" -> optional(boolean)
          .verifying(FormKeys.subscriptionConfirmationNoChoiceKey, _.isDefined)
      )(ChangeSubscriptionConfirmationForm.apply)(ChangeSubscriptionConfirmationForm.unapply)
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

final case class DeleteRedirectConfirmationForm(redirectUri: String,
                                                 deleteRedirectConfirm: Option[String] = Some(""))

object DeleteRedirectConfirmationForm {

  def form: Form[DeleteRedirectConfirmationForm] = Form(
    mapping(
      "redirectUri" -> text,
      "deleteRedirectConfirm" -> optional(text)
        .verifying(FormKeys.deleteRedirectConfirmationNoChoiceKey, s => s.isDefined)
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
      "removeConfirm" -> optional(text)
        .verifying(FormKeys.remove2SVConfirmNoChoiceKey, s => s.isDefined)
    )(Remove2SVConfirmForm.apply)(Remove2SVConfirmForm.unapply)
  )
}

final case class AddAnotherCidrBlockConfirmForm(confirm: Option[String] = Some(""))

object AddAnotherCidrBlockConfirmForm {

  def form: Form[AddAnotherCidrBlockConfirmForm] = Form(
    mapping(
      "confirm" -> optional(text)
        .verifying(FormKeys.ipAllowlistAddAnotherNoChoiceKey, s => s.isDefined)
    )(AddAnotherCidrBlockConfirmForm.apply)(AddAnotherCidrBlockConfirmForm.unapply)
  )
}

final case class AddCidrBlockForm(ipAddress: String)

object AddCidrBlockForm {
  val form: Form[AddCidrBlockForm] = Form(
    mapping("ipAddress" -> cidrBlockValidator)(AddCidrBlockForm.apply)(AddCidrBlockForm.unapply)
  )
}

final case class TaxRegimeEmailPreferencesForm(taxRegime: List[String])

object TaxRegimeEmailPreferencesForm {
  def nonEmptyList: Constraint[Seq[String]] = Constraint[Seq[String]]("constraint.required") { o =>
    if (o.nonEmpty) Valid else Invalid(ValidationError("error.selectedcategories.empty"))
  }

  val form: Form[TaxRegimeEmailPreferencesForm] =
    Form(mapping("taxRegime" -> list(text).verifying(nonEmptyList))
    (TaxRegimeEmailPreferencesForm.apply)(TaxRegimeEmailPreferencesForm.unapply))
}

final case class SelectedApisEmailPreferencesForm(selectedApi: Seq[String], currentCategory: String)

object SelectedApisEmailPreferencesForm {
  def nonEmptyList: Constraint[Seq[String]] = Constraint[Seq[String]]("constraint.required") { o =>
    if (o.nonEmpty) Valid else Invalid(ValidationError("error.selectedapis.empty"))
  }

  def form: Form[SelectedApisEmailPreferencesForm] = Form(mapping(
    "selectedApi" -> seq(text).verifying(nonEmptyList),
    "currentCategory" -> text)
  (SelectedApisEmailPreferencesForm.apply)(SelectedApisEmailPreferencesForm.unapply))
}

final case class SelectedTopicsEmailPreferencesForm(topic: Seq[String])

object SelectedTopicsEmailPreferencesForm {
  def nonEmptyList: Constraint[Seq[String]] = Constraint[Seq[String]]("constraint.required") { o =>
    if (o.nonEmpty) Valid else Invalid(ValidationError("error.selectedtopics.empty"))
  }

  def form: Form[SelectedTopicsEmailPreferencesForm] = Form(mapping(
    "topic" -> seq(text).verifying(nonEmptyList))
  (SelectedTopicsEmailPreferencesForm.apply)(SelectedTopicsEmailPreferencesForm.unapply))
}
