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

package unit.controllers

import controllers._
import play.api.data.FormError
import uk.gov.hmrc.play.test.UnitSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._

class FormValidationSpec extends UnitSpec with Matchers {
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
      val boundForm = ChangePasswordForm.form.bind(validChangePasswordForm + ("password" -> ""))
      boundForm.errors shouldBe List(FormError("password", List("password.error.not.valid.field")), FormError("password", List("password.error.required.field")))
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
      val boundForm = PasswordResetForm.form.bind(validPasswordResetForm + ("password" -> ""))
      boundForm.errors shouldBe List(FormError("password", List("password.error.not.valid.field")), FormError("password", List("password.error.required.field")))
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
      "firstname" -> "john",
      "lastname" -> "smith",
      "emailaddress" -> "john.smith@example.com",
      "password" -> "A1@wwwwwwwww",
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
      val boundForm = RegistrationForm.form.bind(validRegistrationForm
        + ("firstname" -> "")
        + ("lastname" -> "")
        + ("emailaddress" -> "")
        + ("password" -> "")
        + ("confirmpassword" -> ""))
      boundForm.errors shouldBe List(
        FormError("firstname", List("firstname.error.required.field")),
        FormError("lastname", List("lastname.error.required.field")),
        FormError("emailaddress", List("emailaddress.error.required.field")),
        FormError("password", List("password.error.not.valid.field")),
        FormError("password", List("password.error.required.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate lastname and generate error when empty" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm + ("lastname" -> ""))
      boundForm.errors shouldBe List(FormError("lastname", List("lastname.error.required.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate password and generate error when empty" in {
      val boundForm = RegistrationForm.form.bind(validRegistrationForm + ("password" -> ""))
      boundForm.errors shouldBe List(FormError("password", List("password.error.not.valid.field")), FormError("password", List("password.error.required.field")))
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

  "AddApplicationForm " should {
    val validAddAplicationForm = Map("applicationName" -> "Application name", "environment" -> "PRODUCTION",
      "description" -> "Application description")

    "validate a valid form" in {
      val boundForm = AddApplicationForm.form.bind(validAddAplicationForm)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    "validate name in wrong format and generate error when an name is not valid" in {
      val boundForm = AddApplicationForm.form.bind(validAddAplicationForm + ("applicationName" -> "a"))
      boundForm.errors shouldBe List(FormError("applicationName", List("application.name.invalid.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate a valid form with empty description" in {
      val boundForm = AddApplicationForm.form.bind(validAddAplicationForm + ("description" -> ""))
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

  }

  "EditApplicationForm " should {
    val validEditApplicationForm = Map(
      "applicationId" -> "Application ID",
      "applicationName" -> "Application name",
      "originalApplicationName" -> "Application name",
      "description" -> "Application description",
      "redirectUris[0]" -> "https://redirect-url.gov.uk",
      "privacyPolicyUrl" -> "http://redirectprivacy-policy.gov.uk",
      "termsAndConditionsUrl" -> "http://termsandconditions.gov.uk")

    "validate a valid form" in {
      val boundForm = EditApplicationForm.form.bind(validEditApplicationForm)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    "validate name in wrong format and generate error when an name is not valid" in {
      val boundForm = EditApplicationForm.form.bind(validEditApplicationForm + ("applicationName" -> "a"))
      boundForm.errors shouldBe List(FormError("applicationName", List("application.name.invalid.field")))
      boundForm.globalErrors shouldBe List()
    }

    "validate a valid form with empty optional fields" in {
      val boundForm = EditApplicationForm.form.bind(validEditApplicationForm +
        ("description" -> "",
          "privacyPolicyUrl" -> "",
          "termsAndConditionsUrl" -> ""))
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }
  }

  "SubmitApplicationNameForm" should {
     val validSubmitApplicationNameForm = Map(
       "applicationId" -> "Application ID",
       "applicationName" -> "Application name",
       "originalApplicationName" -> "Application name",
       "password" -> "A2@wwwwwwwww")

        "validate a valid form" in {
          val boundForm = SubmitApplicationNameForm.form.bind(validSubmitApplicationNameForm)
          boundForm.errors shouldBe List()
          boundForm.globalErrors shouldBe List()
        }

        "validate name in wrong format and generate error when an name is not valid" in {
          val boundForm = SubmitApplicationNameForm.form.bind(validSubmitApplicationNameForm + ("applicationName" -> "a"))
          boundForm.errors shouldBe List(FormError("applicationName", List("application.name.invalid.field")))
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

    val VALID_FORM: Map[String, String] = Map("rating" -> "", "improvementSuggestions" -> "",
      "name" -> "", "email" -> "", "isJavascript" -> "false")

    "accept no improvement suggestions and no ratings" in {
      val emptySignoutSurveyForm: Map[String, String] = VALID_FORM

      validateNoErrors(emptySignoutSurveyForm)
    }

    "accept improvement suggestions with up to 2000 chars." in {
      val signoutSurveyFormWithImprovement = VALID_FORM + ("improvementSuggestions" -> "a" * 2000)

      validateNoErrors(signoutSurveyFormWithImprovement)

    }

    "accept a rating between 1 and 5 inclusive" in {
      Seq("1", "2", "3", "4", "5").foreach( x => {
        val ratingSignoutSurveyForm = VALID_FORM + ("rating" -> x)
        validateNoErrors(ratingSignoutSurveyForm)
      }
      )
    }

    "reject an improvement suggestion with more than 2000 charaters" in {
      val signoutSurveyFormWithTooLongImprovement = VALID_FORM + ("improvementSuggestions" -> "a" * 2001)

      val boundForm = SignOutSurveyForm.form.bind(signoutSurveyFormWithTooLongImprovement)
      val err = boundForm.errors.head
      err.key shouldBe "improvementSuggestions"
      err.messages shouldBe List("error.maxLength")
    }

    "reject a form that has missing improvement suggestion field" in {
      val signoutSurveyFormWithNoImprovementField = VALID_FORM - "improvementSuggestions"

      val boundForm = SignOutSurveyForm.form.bind(signoutSurveyFormWithNoImprovementField)
      val err = boundForm.errors.head
      err.key shouldBe "improvementSuggestions"
      err.messages shouldBe List("error.required")
    }

    "accept a form that has missing ratings field" in {
      val signoutSurveyFormWithNoRatingsField = VALID_FORM - "rating"

      validateNoErrors(signoutSurveyFormWithNoRatingsField)
    }

    "reject any rating outside of the 1 to 5 range" in {
      Seq(("-1", "error.min"), ("0", "error.min"), ("6", "error.max")).foreach(
        Function.tupled((x: String, err: String) => {
          val ratingSignoutSurveyForm = VALID_FORM + ("rating" -> x)
          val boundForm = SignOutSurveyForm.form.bind(ratingSignoutSurveyForm)
          val error = boundForm.errors.head
          error.key shouldBe "rating"
          error.messages shouldBe List(err)
        }))
    }
  }

  "SupportEnquiryForm" should {
    def validateNoErrors(formData: Map[String, String]): Unit = {
      val boundForm = SupportEnquiryForm.form.bind(formData)
      boundForm.errors shouldBe List()
      boundForm.globalErrors shouldBe List()
    }

    val VALID_FORM = Map(
      "fullname" -> "Terry Jones",
      "emailaddress" -> "test@example.com",
      "comments" -> "this is fine")

    "accept valid form" in {
      validateNoErrors(VALID_FORM)
    }

    "accept comments with up to 3000 chars." in {
      val formData = VALID_FORM + ("comments" -> "a" * 3000)
      validateNoErrors(formData)
    }

    "reject when comments with more than 3000 charaters" in {
      val formData = VALID_FORM + ("comments" -> "a" * 3001)
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err = boundForm.errors.head
      err.key shouldBe "comments"
      err.messages shouldBe List("comments.error.maxLength.field")
    }

    "reject a form that has missing comments" in {
      val formData = VALID_FORM - "comments"
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err = boundForm.errors.head
      err.key shouldBe "comments"
      err.messages shouldBe List("error.required")
    }

    "reject a form that has missing name" in {
      val formData = VALID_FORM - "fullname"
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err = boundForm.errors.head
      err.key shouldBe "fullname"
      err.messages shouldBe List("error.required")
    }

    "reject a form that when the name is too long" in {
      val formData = VALID_FORM + ("fullname" -> "a" * 101)
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err = boundForm.errors.head
      err.key shouldBe "fullname"
      err.messages shouldBe List("fullname.error.maxLength.field")
    }

    "reject a form that has missing email address" in {
      val formData = VALID_FORM - "emailaddress"
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err = boundForm.errors.head
      err.key shouldBe "emailaddress"
      err.messages shouldBe List("error.required")
    }

    "reject a form that when the email is too long" in {
      val formData = VALID_FORM + ("emailaddress" -> s"${"a" * 320}@example.com")
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err = boundForm.errors.head
      err.key shouldBe "emailaddress"
      err.messages shouldBe List("emailaddress.error.maxLength.field")
    }
  }
}
