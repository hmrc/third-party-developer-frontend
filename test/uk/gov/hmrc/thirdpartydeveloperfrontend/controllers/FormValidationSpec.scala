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

import play.api.data.{Form, FormError}

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class FormValidationSpec extends AsyncHmrcSpec {

  private def buildValidateNoErrors[T](bind: Map[String, String] => Form[T])(formData: Map[String, String]): Unit = {
    val boundForm = bind(formData)
    boundForm.errors shouldBe List()
    boundForm.globalErrors shouldBe List()
  }

  "ForgotPasswordForm " should {
    val validForgotPasswordForm = Map("emailaddress" -> "john.smith@example.com")

    "validate a valid form" in {
      val boundForm = ForgotPasswordForm.form.bind(validForgotPasswordForm)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    "validate email in wrong format and generate error when an email is not valid" in {
      val boundForm = ForgotPasswordForm.form.bind(validForgotPasswordForm + ("emailaddress" -> "notvalid"))
      boundForm.errors shouldBe List(FormError("emailaddress", List("emailaddress.error.not.valid.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate empty email and generate error" in {
      val boundForm = ForgotPasswordForm.form.bind(validForgotPasswordForm + ("emailaddress" -> ""))
      boundForm.errors shouldBe List(FormError("emailaddress", List("emailaddress.error.required.field")))
      boundForm.globalErrors shouldBe List()
    }
  }

  "LoginForm" should {
    val validLoginForm = Map("emailaddress" -> "john.smith@example.com", "password" -> "A2@wwwwwwwww")
    "validate a valid form" in {
      val boundForm = LoginForm.form.bind(validLoginForm)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    "validate email in wrong format and generate error when an email is not valid" in {
      val boundForm = LoginForm.form.bind(validLoginForm + ("emailaddress" -> "notvalid"))
      boundForm.errors shouldBe List(FormError("emailaddress", List("emailaddress.error.not.valid.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate empty email and generate error" in {
      val boundForm = LoginForm.form.bind(validLoginForm + ("emailaddress" -> ""))
      boundForm.errors shouldBe List(FormError("emailaddress", List("emailaddress.error.required.field")))
      boundForm.globalErrors shouldBe List()
    }
  }

  "ChangePasswordForm" should {
    val validChangePasswordForm = Map("currentpassword" -> "A1@wwwwwwwww", "password" -> "A2@wwwwwwwww", "confirmpassword" -> "A2@wwwwwwwww")
    "validate a valid form" in {
      val boundForm = ChangePasswordForm.form.bind(validChangePasswordForm)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    "validate currentpassword and generate error if empty" in {
      val boundForm = ChangePasswordForm.form.bind(validChangePasswordForm + ("currentpassword" -> ""))
      boundForm.errors shouldBe List(FormError("currentpassword", List("currentpassword.error.required.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate password and generate error if empty" in {
      val boundForm            = ChangePasswordForm.form.bind(validChangePasswordForm + ("password" -> ""))
      val invalidPasswordError = FormError("password", List("password.error.not.valid.field"))
      val missingPasswordError = FormError("password", List("password.error.required.field"))
      boundForm.errors shouldBe List(invalidPasswordError, missingPasswordError)
      boundForm.globalErrors shouldBe List()
    }

    "validate confirmpassword and generate error if empty" in {
      val boundForm = ChangePasswordForm.form.bind(validChangePasswordForm + ("confirmpassword" -> ""))
      boundForm.errors shouldBe List(FormError("", List("password.error.no.match.global")))
      boundForm.globalErrors shouldBe List(FormError("", List("password.error.no.match.global")))
    }

    "validate password and confirmpassword and generate error when they does not match" in {
      val boundForm = ChangePasswordForm.form.bind(validChangePasswordForm + ("password" -> "A2@wwwwwwwww") + ("confirmpassword" -> "A2@wwwwwwwww2"))
      boundForm.errors shouldBe List(FormError("", List("password.error.no.match.global")))
      boundForm.globalErrors shouldBe List(FormError("", List("password.error.no.match.global")))
    }
  }

  "PasswordResetForm" should {
    val validPasswordResetForm = Map("password" -> "A2@wwwwwwwww", "confirmpassword" -> "A2@wwwwwwwww")
    "validate a valid form" in {
      val boundForm = PasswordResetForm.form.bind(validPasswordResetForm)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    "validate password and generate error if empty" in {
      val boundForm            = PasswordResetForm.form.bind(validPasswordResetForm + ("password" -> ""))
      val invalidPasswordError = FormError("password", List("password.error.not.valid.field"))
      val missingPasswordError = FormError("password", List("password.error.required.field"))
      boundForm.errors shouldBe List(invalidPasswordError, missingPasswordError)
      boundForm.globalErrors shouldBe List()
    }

    "validate confirmpassword and generate error if empty" in {
      val boundForm = PasswordResetForm.form.bind(validPasswordResetForm + ("confirmpassword" -> ""))
      boundForm.errors shouldBe List(FormError("", List("password.error.no.match.global")))
      boundForm.globalErrors shouldBe List(FormError("", List("password.error.no.match.global")))
    }

    "validate password and confirmpassword and generate error when they does not match" in {
      val boundForm = PasswordResetForm.form.bind(validPasswordResetForm + ("password" -> "A2@wwwwwwwww") + ("confirmpassword" -> "A2@wwwwwwwww2"))
      boundForm.errors shouldBe List(FormError("", List("password.error.no.match.global")))
      boundForm.globalErrors shouldBe List(FormError("", List("password.error.no.match.global")))
    }
  }

  "RegistrationForm " should {
    val validRegistrationForm = Map(
      "firstname"       -> "john",
      "lastname"        -> "smith",
      "emailaddress"    -> "john.smith@example.com",
      "password"        -> "A1@wwwwwwwww",
      "confirmpassword" -> "A1@wwwwwwwww"
    )

    "validate a valid form" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    "validate firstname and generate error when empty" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm + ("firstname" -> ""))
      boundForm.errors shouldBe List(FormError("firstname", List("firstname.error.required.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate all fields with error" in {
      val boundForm = RegistrationForm.form.bind(
        validRegistrationForm
          + ("firstname"       -> "")
          + ("lastname"        -> "")
          + ("emailaddress"    -> "")
          + ("password"        -> "")
          + ("confirmpassword" -> "")
      )
      boundForm.errors shouldBe List(
        FormError("firstname", List("firstname.error.required.field")),
        FormError("lastname", List("lastname.error.required.field")),
        FormError("emailaddress", List("emailaddress.error.required.field")),
        FormError("password", List("password.error.not.valid.field")),
        FormError("password", List("password.error.required.field"))
      )
      boundForm.globalErrors shouldBe List()
    }

    "validate lastname and generate error when empty" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm + ("lastname" -> ""))
      boundForm.errors shouldBe List(FormError("lastname", List("lastname.error.required.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate password and generate error when empty" in {
      val boundForm            = RegistrationForm.form.bind(validRegistrationForm + ("password" -> ""))
      val invalidPasswordError = FormError("password", List("password.error.not.valid.field"))
      val missingPasswordError = FormError("password", List("password.error.required.field"))
      boundForm.errors shouldBe List(invalidPasswordError, missingPasswordError)
      boundForm.globalErrors shouldBe List()
    }

    "validate password and generate error when not strong enough" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm + ("password" -> "notstrongenough"))
      boundForm.errors shouldBe List(FormError("password", List("password.error.not.valid.field")))
      boundForm.globalErrors shouldBe List()

    }

    "validate confirmpassword and generate global error when empty" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm + ("confirmpassword" -> ""))
      boundForm.errors shouldBe List(FormError("", List("password.error.no.match.global")))
      boundForm.globalErrors shouldBe List(FormError("", List("password.error.no.match.global")))
    }

    "validate password and confirmpassword and generate error when they does not match" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm + ("password" -> "A1@wwwwwwwww") + ("confirmpassword" -> "A1@wwwwwwww2"))
      boundForm.errors shouldBe List(FormError("", List("password.error.no.match.global")))
      boundForm.globalErrors shouldBe List(FormError("", List("password.error.no.match.global")))

    }

    "validate email and generate error when not valid" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm + ("emailaddress" -> "notValid"))
      boundForm.errors shouldBe List(FormError("emailaddress", List("emailaddress.error.not.valid.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate email and generate error when empty" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm + ("emailaddress" -> ""))
      boundForm.errors shouldBe List(FormError("emailaddress", List("emailaddress.error.required.field")))
      boundForm.globalErrors shouldBe List()
    }
  }

  "EditApplicationForm " should {
    val validEditApplicationForm = Map(
      "applicationId"           -> "Application ID",
      "applicationName"         -> "Application name",
      "originalApplicationName" -> "Application name",
      "description"             -> "Application description",
      "redirectUris[0]"         -> "https://redirect-url.gov.uk",
      "privacyPolicyUrl"        -> "http://redirectprivacy-policy.gov.uk",
      "termsAndConditionsUrl"   -> "http://termsandconditions.gov.uk",
      "grantLength"             -> "12 months"
    )

    "validate a valid form" in {
      val boundForm = EditApplicationForm.form.bind(validEditApplicationForm)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    "validate name in wrong format and generate error when an name is not valid" in {
      val boundForm = EditApplicationForm.form.bind(validEditApplicationForm + ("applicationName" -> "a"))
      boundForm.errors shouldBe List(FormError("applicationName", List("application.name.invalid.length.and.characters")))
      boundForm.globalErrors shouldBe List()
    }

    "validate a valid form with empty optional fields" in {
      val boundForm = EditApplicationForm.form.bind(
        validEditApplicationForm +
          ("description"          -> "",
          "privacyPolicyUrl"      -> "",
          "termsAndConditionsUrl" -> "")
      )
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }
  }

  "SubmitApplicationNameForm" should {
    val validSubmitApplicationNameForm =
      Map("applicationId" -> "Application ID", "applicationName" -> "Application name", "originalApplicationName" -> "Application name", "password" -> "A2@wwwwwwwww")

    "validate a valid form" in {
      val boundForm = SubmitApplicationNameForm.form.bind(validSubmitApplicationNameForm)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    "validate name in wrong format and generate error when an name is not valid" in {
      val boundForm = SubmitApplicationNameForm.form.bind(validSubmitApplicationNameForm + ("applicationName" -> "a"))
      boundForm.errors shouldBe List(FormError("applicationName", List("application.name.invalid.length.and.characters")))
      boundForm.globalErrors shouldBe List()
    }

    "validate password and generate error if empty" in {
      val boundForm = SubmitApplicationNameForm.form.bind(validSubmitApplicationNameForm + ("password" -> ""))
      boundForm.errors shouldBe List(FormError("password", List("error.required")))
      boundForm.globalErrors shouldBe List()
    }
  }

  "SignoutSurveyForm" should {
    def validateNoErrors(formData: Map[String, String]): Unit = {
      val boundForm = SignOutSurveyForm.form.bind(formData)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    val validFormData: Map[String, String] = Map("rating" -> "", "improvementSuggestions" -> "", "name" -> "", "email" -> "", "isJavascript" -> "false")

    "accept no improvement suggestions and no ratings" in {
      val emptySignoutSurveyForm: Map[String, String] = validFormData

      validateNoErrors(emptySignoutSurveyForm)
    }

    "accept improvement suggestions with up to 2000 chars." in {
      val signoutSurveyFormWithImprovement = validFormData + ("improvementSuggestions" -> "a" * 2000)

      validateNoErrors(signoutSurveyFormWithImprovement)

    }

    "accept a rating between 1 and 5 inclusive" in {
      Seq("1", "2", "3", "4", "5").foreach(x => {
        val ratingSignoutSurveyForm = validFormData + ("rating" -> x)
        validateNoErrors(ratingSignoutSurveyForm)
      })
    }

    "reject an improvement suggestion with more than 2000 charaters" in {
      val signoutSurveyFormWithTooLongImprovement = validFormData + ("improvementSuggestions" -> "a" * 2001)

      val boundForm = SignOutSurveyForm.form.bind(signoutSurveyFormWithTooLongImprovement)
      val err       = boundForm.errors.head
      err.key shouldBe "improvementSuggestions"
      err.messages shouldBe List("error.maxLength")
    }

    "reject a form that has missing improvement suggestion field" in {
      val signoutSurveyFormWithNoImprovementField = validFormData - "improvementSuggestions"

      val boundForm = SignOutSurveyForm.form.bind(signoutSurveyFormWithNoImprovementField)
      val err       = boundForm.errors.head
      err.key shouldBe "improvementSuggestions"
      err.messages shouldBe List("error.required")
    }

    "accept a form that has missing ratings field" in {
      val signoutSurveyFormWithNoRatingsField = validFormData - "rating"

      validateNoErrors(signoutSurveyFormWithNoRatingsField)
    }

    "reject any rating outside of the 1 to 5 range" in {
      Seq(("-1", "error.min"), ("0", "error.min"), ("6", "error.max")).foreach(Function.tupled((x: String, err: String) => {
        val ratingSignoutSurveyForm = validFormData + ("rating" -> x)
        val boundForm               = SignOutSurveyForm.form.bind(ratingSignoutSurveyForm)
        val error                   = boundForm.errors.head
        error.key shouldBe "rating"
        error.messages shouldBe List(err)
      }))
    }
  }

  "SupportEnquiryForm" should {
    def validateNoErrors = buildValidateNoErrors(SupportEnquiryForm.form.bind) _

    val validFormData = Map("fullname" -> "Terry Jones", "emailaddress" -> "test@example.com", "comments" -> "this is fine")

    "accept valid form" in {
      validateNoErrors(validFormData)
    }

    "accept comments with up to 3000 chars." in {
      val formData = validFormData + ("comments" -> "a" * 3000)
      validateNoErrors(formData)
    }

    "reject when comments with more than 3000 charaters" in {
      val formData  = validFormData + ("comments" -> "a" * 3001)
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "comments"
      err.messages shouldBe List("comments.error.maxLength.field")
    }

    "reject a form that has missing comments" in {
      val formData  = validFormData - "comments"
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "comments"
      err.messages shouldBe List("error.required")
    }

    "reject a form that has missing name" in {
      val formData  = validFormData - "fullname"
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "fullname"
      err.messages shouldBe List("error.required")
    }

    "reject a form that when the name is too long" in {
      val formData  = validFormData + ("fullname" -> "a" * 101)
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "fullname"
      err.messages shouldBe List("fullname.error.maxLength.field")
    }

    "reject a form that has missing email address" in {
      val formData  = validFormData - "emailaddress"
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "emailaddress"
      err.messages shouldBe List("error.required")
    }

    "reject a form that when the email is too long" in {
      val formData  = validFormData + ("emailaddress" -> s"${"a" * 320}@example.com")
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "emailaddress"
      err.messages shouldBe List("emailaddress.error.maxLength.field")
    }
  }

  "ProfileForm" should {
    def validateNoErrors = buildValidateNoErrors(ProfileForm.form.bind) _

    val validFormData = Map("firstname" -> "Terry", "lastname" -> "Jones", "organisation" -> "HMRC")

    "accept valid form" in {
      validateNoErrors(validFormData)
    }

    "accept valid form without organisation" in {
      validateNoErrors(validFormData + ("organisation" -> ""))
    }

    "reject a form when the first name is too long" in {
      val formData  = validFormData + ("firstname" -> "a" * 31)
      val boundForm = ProfileForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "firstname"
      err.messages shouldBe List("firstname.error.maxLength.field")
    }

    "reject a form when the first name is empty" in {
      val formData  = validFormData + ("firstname" -> "")
      val boundForm = ProfileForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "firstname"
      err.messages shouldBe List("firstname.error.required.field")
    }

    "reject a form when the last name is too long" in {
      val formData  = validFormData + ("lastname" -> "a" * 31)
      val boundForm = ProfileForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "lastname"
      err.messages shouldBe List("lastname.error.maxLength.field")
    }

    "reject a form when the last name is empty" in {
      val formData  = validFormData + ("lastname" -> "")
      val boundForm = ProfileForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "lastname"
      err.messages shouldBe List("lastname.error.required.field")
    }
  }

  "SelectApisFromSubscriptionsForm" should {
    def validateNoErrors = buildValidateNoErrors(SelectApisFromSubscriptionsForm.form.bind) _

    val validFormData = Map("selectedApi[0]" -> "", "applicationId" -> ApplicationId.random.value)

    "accept valid form" in {
      validateNoErrors(validFormData)
    }

    "accept valid form without any selected apis" in {
      validateNoErrors(validFormData + ("selectedApis[0]" -> ""))
    }
  }

  "SelectTopicsFromSubscriptionsForm" should {
    def validateNoErrors = buildValidateNoErrors(SelectTopicsFromSubscriptionsForm.form.bind) _

    val validFormData = Map("topic[0]" -> "TopicOne", "applicationId" -> ApplicationId.random.value)

    "accept valid form" in {
      validateNoErrors(validFormData)
    }

    "reject a form when now topic is supplied" in {
      val formDataWithoutTopic = Map("applicationId" -> ApplicationId.random.value)
      val boundForm            = SelectTopicsFromSubscriptionsForm.form.bind(formDataWithoutTopic)
      val err                  = boundForm.errors.head
      err.key shouldBe "topic"
      err.messages shouldBe List("error.selectedtopics.nonselected.field")
    }
  }

  "ChangeOfPrivacyPolicyLocationForm" should {
    def validateNoErrors = buildValidateNoErrors(ChangeOfPrivacyPolicyLocationForm.form.bind) _

    "accept valid form with valid url" in {
      val validFormDataWithUrl = Map("privacyPolicyUrl" -> "http://example.com", "isInDesktop" -> "false")
      validateNoErrors(validFormDataWithUrl)
    }

    "accept valid form with in desktop" in {
      val validFormDataWithInDesktop = Map("privacyPolicyUrl" -> "", "isInDesktop" -> "true")
      validateNoErrors(validFormDataWithInDesktop)
    }

    "reject form with invalid url" in {
      val invalidFormDataWithBadUrl = Map("privacyPolicyUrl" -> "not a url", "isInDesktop" -> "false")
      val boundForm                 = ChangeOfPrivacyPolicyLocationForm.form.bind(invalidFormDataWithBadUrl)
      boundForm.errors.head.key shouldBe ""
      boundForm.errors.head.messages shouldBe List("application.privacypolicylocation.invalid.badurl")
    }
  }

  "ChangeOfTermsAndConditionsLocationForm" should {
    def validateNoErrors = buildValidateNoErrors(ChangeOfTermsAndConditionsLocationForm.form.bind) _

    "accept valid form with valid url" in {
      val validFormDataWithUrl = Map("termsAndConditionsUrl" -> "http://example.com", "isInDesktop" -> "false")
      validateNoErrors(validFormDataWithUrl)
    }

    "accept valid form with in desktop" in {
      val validFormDataWithInDesktop = Map("termsAndConditionsUrl" -> "", "isInDesktop" -> "true")
      validateNoErrors(validFormDataWithInDesktop)
    }

    "reject form with invalid url" in {
      val invalidFormDataWithBadUrl = Map("termsAndConditionsUrl" -> "not a url", "isInDesktop" -> "false")
      val boundForm                 = ChangeOfTermsAndConditionsLocationForm.form.bind(invalidFormDataWithBadUrl)
      boundForm.errors.head.key shouldBe ""
      boundForm.errors.head.messages shouldBe List("application.termsconditionslocation.invalid.badurl")
    }
  }

  "ResponsibleIndividualChangeToSelfOrOtherForm" should {
    def validateNoErrors = buildValidateNoErrors(ResponsibleIndividualChangeToSelfOrOtherForm.form.bind) _

    "accept valid form with 'who' value of 'self'" in {
      validateNoErrors(Map("who" -> "self"))
    }
    "accept valid form with 'who' value of 'other'" in {
      validateNoErrors(Map("who" -> "other"))
    }
    "not accept form with 'who' value of something else" in {
      val boundForm = ResponsibleIndividualChangeToSelfOrOtherForm.form.bind(Map("who" -> "him over there"))
      boundForm.errors.head.key shouldBe "who"
      boundForm.errors.head.messages shouldBe List("error.unknown")
    }
    "not accept form with missing 'who' value" in {
      val boundForm = ResponsibleIndividualChangeToSelfOrOtherForm.form.bind(Map("name" -> "bob"))
      boundForm.errors.head.key shouldBe "who"
      boundForm.errors.head.messages shouldBe List("error.required")
    }
  }
}
