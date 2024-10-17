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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import java.util.UUID

import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Form, FormError}

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, ApplicationWithCollaborators, GrantLength}
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{
  PrivacyPolicyLocation,
  PrivacyPolicyLocations,
  TermsAndConditionsLocation,
  TermsAndConditionsLocations
}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Conversions._

// scalastyle:off number.of.types

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
        routes.Registration.resendVerification()
      )
      .withGlobalError(FormKeys.accountUnverifiedGlobalKey)
  }

  val form: Form[LoginForm] = Form(
    mapping(
      "emailaddress" -> emailValidator(),
      "password"     -> loginPasswordValidator
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
        routes.Registration.resendVerification()
      )
      .withGlobalError(FormKeys.accountUnverifiedGlobalKey)
  }

  val form: Form[PasswordResetForm] = Form(
    mapping(
      "password"        -> passwordValidator,
      "confirmpassword" -> text
    )(PasswordResetForm.apply)(PasswordResetForm.unapply).verifying(passwordsMatch)
  )

}

case class RegisterForm(firstName: String, lastName: String, emailaddress: String, password: String, confirmPassword: String)
    extends ConfirmPassword

object RegistrationForm {

  val form: Form[RegisterForm] = Form(
    mapping(
      "firstname"       -> firstnameValidator,
      "lastname"        -> lastnameValidator,
      "emailaddress"    -> emailValidator(),
      "password"        -> passwordValidator,
      "confirmpassword" -> text
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

case class ProfileForm(firstName: String, lastName: String)

object ProfileForm {

  lazy val form = Form(
    mapping(
      "firstname" -> firstnameValidator,
      "lastname"  -> lastnameValidator
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
        routes.Registration.resendVerification()
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
        routes.Registration.resendVerification()
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
      "password"        -> passwordValidator,
      "confirmpassword" -> text
    )(ChangePasswordForm.apply)(ChangePasswordForm.unapply).verifying(passwordsMatch)
  )

}

final case class RemoveTeamMemberConfirmationForm(email: String, confirm: Option[String] = Some(""))

object RemoveTeamMemberConfirmationForm {

  val form: Form[RemoveTeamMemberConfirmationForm] = Form(
    mapping(
      "email"   -> emailValidator(FormKeys.teamMemberEmailRequired),
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
      "role"  -> optional(text)
        .verifying(FormKeys.teamMemberRoleRequired, selection => selection.isDefined)
    )(AddTeamMemberForm.apply)(AddTeamMemberForm.unapply)
  )
}

case class ChooseApplicationToUpliftForm(applicationId: ApplicationId)

object ChooseApplicationToUpliftForm {

  val form: Form[ChooseApplicationToUpliftForm] = Form(
    mapping(
      "applicationId" -> optional(text).verifying("choose.application.to.uplift.error", s => s.isDefined && !s.get.isEmpty()).transform[ApplicationId](
        s => ApplicationId(UUID.fromString(s.get)),
        id => Some(id.value.toString)
      )
    )(ChooseApplicationToUpliftForm.apply)(ChooseApplicationToUpliftForm.unapply)
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

case class EditApplicationForm(
    applicationId: ApplicationId,
    applicationName: String,
    description: Option[String] = None,
    privacyPolicyUrl: Option[String] = None,
    termsAndConditionsUrl: Option[String] = None,
    grantLength: String
  )

object EditApplicationForm {

  val form: Form[EditApplicationForm] = Form(
    mapping(
      "applicationId"         -> nonEmptyText.transform[ApplicationId](text => ApplicationId(java.util.UUID.fromString(text)), id => id.toString()),
      "applicationName"       -> nonEmptyText.verifying(applicationNameContraint),
      "description"           -> optional(text),
      "privacyPolicyUrl"      -> optional(privacyPolicyUrlValidator),
      "termsAndConditionsUrl" -> optional(tNcUrlValidator),
      "grantLength"           -> text
    )(EditApplicationForm.apply)(EditApplicationForm.unapply)
  )

  def withData(app: ApplicationWithCollaborators) = {
    val (privacyPolicyLocation, termsAndConditionsLocation) = app.access match {
      case a: Access.Standard => (a.privacyPolicyLocation, a.termsAndConditionsLocation)
      case _                  => (None, None)
    }

    val privacyPolicyUrl: Option[String]      = privacyPolicyLocation collect { case PrivacyPolicyLocations.Url(u) => u }
    val termsAndConditionsUrl: Option[String] = termsAndConditionsLocation collect { case TermsAndConditionsLocations.Url(u) => u }

    form.fillAndValidate(
      EditApplicationForm(
        app.id,
        app.name.value,
        app.details.description,
        privacyPolicyUrl,
        termsAndConditionsUrl,
        GrantLength.show(app.details.grantLength)
      )
    )
  }
}

case class SubmitApplicationNameForm(applicationName: String, originalApplicationName: String, password: String = "") extends PasswordConfirmation

object SubmitApplicationNameForm {

  val form: Form[SubmitApplicationNameForm] = Form(
    mapping(
      "applicationName"         -> applicationNameValidator,
      "originalApplicationName" -> nonEmptyText,
      "password"                -> nonEmptyText
    )(SubmitApplicationNameForm.apply)(SubmitApplicationNameForm.unapply)
  )
}

case class SignOutSurveyForm(rating: Option[Int], improvementSuggestions: String, name: String, email: String, isJavascript: Boolean)

object SignOutSurveyForm {

  val form: Form[SignOutSurveyForm] = Form(
    mapping(
      "rating"                 -> optional(number(1, 5)),
      "improvementSuggestions" -> text(0, 2000),
      "name"                   -> text(0, 100),
      "email"                  -> text(0, 100),
      "isJavascript"           -> boolean
    )(SignOutSurveyForm.apply)(SignOutSurveyForm.unapply)
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
        "confirm"    -> optional(boolean)
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

final case class DeleteRedirectConfirmationForm(redirectUri: String, deleteRedirectConfirm: Option[String] = Some(""))

object DeleteRedirectConfirmationForm {

  def form: Form[DeleteRedirectConfirmationForm] = Form(
    mapping(
      "redirectUri"           -> text,
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
      "newRedirectUri"      -> redirectUriValidator
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
    if (o.nonEmpty) Valid else Invalid(ValidationError(FormKeys.selectedCategoryNonSelectedKey))
  }

  val form: Form[TaxRegimeEmailPreferencesForm] =
    Form(mapping("taxRegime" -> list(text).verifying(nonEmptyList))(TaxRegimeEmailPreferencesForm.apply)(TaxRegimeEmailPreferencesForm.unapply))
}

final case class SelectedApisEmailPreferencesForm(apiRadio: Option[String] = Some(""), selectedApi: Seq[String], currentCategory: String)

object SelectedApisEmailPreferencesForm {

  def nonEmpty(message: String): Constraint[String] = Constraint[String] { s: String =>
    if (Option(s).isDefined) Invalid(message) else Valid
  }

  def form: Form[SelectedApisEmailPreferencesForm] = Form(mapping(
    "apiRadio"        -> optional(text)
      .verifying(FormKeys.selectedApiRadioGlobalKey, s => s.isDefined),
    "selectedApi"     -> seq(text),
    "currentCategory" -> text
  )(SelectedApisEmailPreferencesForm.apply)(SelectedApisEmailPreferencesForm.unapply)
    .verifying(
      FormKeys.selectedApisNonSelectedGlobalKey,
      fields =>
        fields match {
          case data: SelectedApisEmailPreferencesForm =>
            if (data.apiRadio.contains("SOME_APIS") && data.selectedApi.isEmpty) false else true
        }
    ))
}

final case class SelectedTopicsEmailPreferencesForm(topic: Seq[String])

object SelectedTopicsEmailPreferencesForm {

  def nonEmptyList: Constraint[Seq[String]] = Constraint[Seq[String]]("constraint.required") { o =>
    if (o.nonEmpty) Valid else Invalid(ValidationError(FormKeys.selectedTopicsNonSelectedKey))
  }

  def form: Form[SelectedTopicsEmailPreferencesForm] = Form(mapping(
    "topic" -> seq(text).verifying(nonEmptyList)
  )(SelectedTopicsEmailPreferencesForm.apply)(SelectedTopicsEmailPreferencesForm.unapply))
}

final case class SelectApisFromSubscriptionsForm(selectedApi: Seq[String], applicationId: ApplicationId)

object SelectApisFromSubscriptionsForm {

  implicit def applicationIdFormat: Formatter[ApplicationId] = new Formatter[ApplicationId] {
    override val format                                       = Some(("format.uuid", Nil))
    override def bind(key: String, data: Map[String, String]) = data.get(key).map(text => ApplicationId(UUID.fromString(text))).toRight(Seq(FormError(key, "error.required", Nil)))
    override def unbind(key: String, value: ApplicationId)    = Map(key -> value.toString)
  }

  def nonEmpty(message: String): Constraint[String] = Constraint[String] { s: String =>
    if (Option(s).isDefined) Invalid(message) else Valid
  }

  def form: Form[SelectApisFromSubscriptionsForm] =
    Form(mapping("selectedApi" -> seq(text), "applicationId" -> of[ApplicationId])(SelectApisFromSubscriptionsForm.apply)(SelectApisFromSubscriptionsForm.unapply)
      .verifying(
        FormKeys.selectedApisNonSelectedGlobalKey,
        fields =>
          fields match {
            case data: SelectApisFromSubscriptionsForm =>
              if (data.selectedApi.isEmpty) false else true
          }
      ))
}

final case class SelectTopicsFromSubscriptionsForm(topic: Seq[String], applicationId: ApplicationId)

object SelectTopicsFromSubscriptionsForm {

  implicit def applicationIdFormat: Formatter[ApplicationId] = new Formatter[ApplicationId] {
    override val format                                       = Some(("format.uuid", Nil))
    override def bind(key: String, data: Map[String, String]) = data.get(key).map(text => ApplicationId(UUID.fromString(text))).toRight(Seq(FormError(key, "error.required", Nil)))
    override def unbind(key: String, value: ApplicationId)    = Map(key -> value.toString())
  }

  def nonEmptyList: Constraint[Seq[String]] = Constraint[Seq[String]]("constraint.required") { o =>
    if (o.nonEmpty) Valid else Invalid(ValidationError(FormKeys.selectedTopicsNonSelectedKey))
  }

  def form: Form[SelectTopicsFromSubscriptionsForm] = Form(
    mapping(
      "topic"         -> seq(text).verifying(nonEmptyList),
      "applicationId" -> of[ApplicationId]
    )(SelectTopicsFromSubscriptionsForm.apply)(SelectTopicsFromSubscriptionsForm.unapply)
  )
}

case class ChangeOfApplicationNameForm(applicationName: String)

object ChangeOfApplicationNameForm {

  val form: Form[ChangeOfApplicationNameForm] = Form(
    mapping(
      "applicationName" -> applicationNameValidator
    )(ChangeOfApplicationNameForm.apply)(ChangeOfApplicationNameForm.unapply)
  )

  def withData(applicationName: ApplicationName) = {
    form.fillAndValidate(
      ChangeOfApplicationNameForm(
        applicationName.value
      )
    )
  }
}

case class ChangeOfPrivacyPolicyLocationForm(privacyPolicyUrl: String, isInDesktop: Boolean, isNewJourney: Boolean) {

  def toLocation: PrivacyPolicyLocation = isInDesktop match {
    case true                               => PrivacyPolicyLocations.InDesktopSoftware
    case false if privacyPolicyUrl.nonEmpty => PrivacyPolicyLocations.Url(privacyPolicyUrl)
    case _                                  => PrivacyPolicyLocations.NoneProvided
  }
}

object ChangeOfPrivacyPolicyLocationForm {

  val validUrlPresentIfNotInDesktop: Constraint[ChangeOfPrivacyPolicyLocationForm] = Constraint({
    form =>
      if (form.isInDesktop || isValidUrl(form.privacyPolicyUrl)) {
        Valid
      } else {
        Invalid(ValidationError("application.privacypolicylocation.invalid.badurl"))
      }
  })

  val form: Form[ChangeOfPrivacyPolicyLocationForm] = Form(
    mapping(
      "privacyPolicyUrl" -> text,
      "isInDesktop"      -> boolean,
      "isNewJourney"     -> boolean
    )(ChangeOfPrivacyPolicyLocationForm.apply)(ChangeOfPrivacyPolicyLocationForm.unapply).verifying(validUrlPresentIfNotInDesktop)
  )

  def withNewJourneyData(privacyPolicyLocation: PrivacyPolicyLocation) = {
    val privacyPolicyUrl = privacyPolicyLocation match {
      case PrivacyPolicyLocations.Url(value) => value
      case _                                 => ""
    }
    val isInDesktop      = privacyPolicyLocation match {
      case PrivacyPolicyLocations.InDesktopSoftware => true
      case _                                        => false
    }
    form.fillAndValidate(
      ChangeOfPrivacyPolicyLocationForm(privacyPolicyUrl, isInDesktop, true)
    )
  }

  def withOldJourneyData(maybePrivacyPolicyUrl: Option[String]) = {
    form.fill(
      ChangeOfPrivacyPolicyLocationForm(maybePrivacyPolicyUrl.getOrElse(""), false, false)
    )
  }
}

case class ChangeOfTermsAndConditionsLocationForm(termsAndConditionsUrl: String, isInDesktop: Boolean, isNewJourney: Boolean) {

  def toLocation: TermsAndConditionsLocation = isInDesktop match {
    case true                                    => TermsAndConditionsLocations.InDesktopSoftware
    case false if !termsAndConditionsUrl.isEmpty => TermsAndConditionsLocations.Url(termsAndConditionsUrl)
    case _                                       => TermsAndConditionsLocations.NoneProvided
  }
}

object ChangeOfTermsAndConditionsLocationForm {

  val validUrlPresentIfNotInDesktop: Constraint[ChangeOfTermsAndConditionsLocationForm] = Constraint({
    form =>
      if (form.isInDesktop || isValidUrl(form.termsAndConditionsUrl)) {
        Valid
      } else {
        Invalid(ValidationError("application.termsconditionslocation.invalid.badurl"))
      }
  })

  val form: Form[ChangeOfTermsAndConditionsLocationForm] = Form(
    mapping(
      "termsAndConditionsUrl" -> text,
      "isInDesktop"           -> boolean,
      "isNewJourney"          -> boolean
    )(ChangeOfTermsAndConditionsLocationForm.apply)(ChangeOfTermsAndConditionsLocationForm.unapply).verifying(validUrlPresentIfNotInDesktop)
  )

  def withNewJourneyData(termsAndConditionsLocation: TermsAndConditionsLocation) = {
    val termsAndConditionsUrl = termsAndConditionsLocation match {
      case TermsAndConditionsLocations.Url(value) => value
      case _                                      => ""
    }
    val isInDesktop           = termsAndConditionsLocation match {
      case TermsAndConditionsLocations.InDesktopSoftware => true
      case _                                             => false
    }
    form.fillAndValidate(
      ChangeOfTermsAndConditionsLocationForm(termsAndConditionsUrl, isInDesktop, true)
    )
  }

  def withOldJourneyData(maybeTermsAndConditionsUrl: Option[String]) = {
    form.fill(
      ChangeOfTermsAndConditionsLocationForm(maybeTermsAndConditionsUrl.getOrElse(""), false, false)
    )
  }
}

case class ResponsibleIndividualChangeToSelfOrOtherForm(who: String)

object ResponsibleIndividualChangeToSelfOrOtherForm {
  val self                = "self"
  val other               = "other"
  val whoField            = "who"
  private val validValues = List(self, other)

  def form(): Form[ResponsibleIndividualChangeToSelfOrOtherForm] = Form(
    mapping(
      whoField -> text.verifying(validValues contains _)
    )(ResponsibleIndividualChangeToSelfOrOtherForm.apply)(ResponsibleIndividualChangeToSelfOrOtherForm.unapply)
  )
}

case class ResponsibleIndividualChangeToOtherForm(name: String, email: String)

object ResponsibleIndividualChangeToOtherForm {

  def form(): Form[ResponsibleIndividualChangeToOtherForm] = Form(
    mapping(
      "name"  -> nonEmptyText,
      "email" -> email
    )(ResponsibleIndividualChangeToOtherForm.apply)(ResponsibleIndividualChangeToOtherForm.unapply)
  )
}

// scalastyle:on number.of.types
