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

package uk.gov.hmrc.thirdpartydeveloperfrontend

import java.net.URL
import scala.util.{Failure, Try}

import org.apache.commons.net.util.SubnetUtils

import play.api.data.Forms.{optional, text}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError, ValidationResult}
import play.api.data.{Forms, Mapping}
import uk.gov.hmrc.emailaddress.EmailAddress

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.RedirectUri
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment

package object controllers {

  object FormKeys {

    val firstnameField        = "firstname"
    val lastnameField         = "lastname"
    val fullnameField         = "fullname"
    val emailaddressField     = "emailaddress"
    val passwordField         = "password"
    val loginPasswordField    = "loginpassword"
    val currentPasswordField  = "currentpassword"
    val confirmapasswordField = "confirmpassword"
    val appNameField          = "applicationName"
    val appDescriptionField   = "description"
    val deleteSelectField     = "deleteSelect"

    val firstnameRequiredKey                = "firstname.error.required.field"
    val firstnameMaxLengthKey               = "firstname.error.maxLength.field"
    val lastnameRequiredKey                 = "lastname.error.required.field"
    val lastnameMaxLengthKey                = "lastname.error.maxLength.field"
    val fullnameRequiredKey                 = "fullname.error.required.field"
    val fullnameMaxLengthKey                = "fullname.error.maxLength.field"
    val commentsRequiredKey                 = "comments.error.required.field"
    val commentsMaxLengthKey                = "comments.error.maxLength.field"
    val commentsSpamKey                     = "comments.error.spam.field"
    val ipAllowlistAddAnotherNoChoiceKey    = "ipAllowlist.addAnother.confirmation.no.choice.field"
    val ipAllowlistInvalidCidrBlockKey      = "ipAllowlist.cidrBlock.invalid"
    val ipAllowlistPrivateCidrBlockKey      = "ipAllowlist.cidrBlock.invalid.private"
    val ipAllowlistInvalidCidrBlockRangeKey = "ipAllowlist.cidrBlock.invalid.range"
    val telephoneRequiredKey                = "telephone.error.required.field"
    val emailaddressRequiredKey             = "emailaddress.error.required.field"
    val emailaddressNotValidKey             = "emailaddress.error.not.valid.field"
    val emailMaxLengthKey                   = "emailaddress.error.maxLength.field"
    val detailsRequiredKey                  = "details.error.required.field"
    val detailsMaxLengthKey                 = "details.error.maxLength.field"

    val termsOfUseAgreeKey       = "termsofuse.error.required.field"
    val termsOfUseAgreeGlobalKey = "termsofuse.error.required.global"

    val passwordNotValidKey = "password.error.not.valid.field"
    val passwordRequiredKey = "password.error.required.field"
    val passwordNoMatchKey  = "password.error.no.match.field"

    val loginPasswordRequiredKey = "loginpassword.error.required.field"

    val emailalreadyInUseKey  = "emailaddress.already.registered.field"
    val emailalreadyInUse2Key = "emailaddress.already.registered.2.field"

    val accountUnverifiedKey        = "account.unverified.field"
    val invalidCredentialsKey       = "invalid.credentials.field"
    val invalidPasswordKey          = "invalid.password.field"
    val accountLockedKey            = "account.locked.field"
    val accountLocked2Key           = "account.locked.2.field"
    val currentPasswordRequiredKey  = "currentpassword.error.required.field"
    val currentPasswordInvalidKey   = "currentpassword.invalid.field"
    val redirectUriInvalidKey       = "redirect.uri.invalid.field"
    val privacyPolicyUrlRequiredKey = "privacy.policy.url.required.field"
    val privacyPolicyUrlInvalidKey  = "privacy.policy.url.invalid.field"
    val privacyPolicyUrlNoChoiceKey = "privacy.policy.url.no.choice.field"
    val tNcUrlInvalidKey            = "terms.conditions.url.invalid.field"
    val tNcUrlNoChoiceKey           = "terms.conditions.url.no.choice.field"
    val tNcUrlRequiredKey           = "terms.conditions.url.required.field"

    val applicationNameInvalidKeyLengthAndCharacters = "application.name.invalid.length.and.characters"

    val applicationNameInvalidKey       = "application.name.invalid.name"
    val applicationNameAlreadyExistsKey = "application.name.already.exists.field"

    val environmentInvalidKey = "environment.error.required.field"

    val teamMemberEmailRequired = "team.member.error.emailAddress.required.field"
    val teamMemberAlreadyExists = "team.member.error.emailAddress.already.exists.field"

    val teamMemberRoleRequired             = "roles.error.answer.required.field.content"
    val removeTeamMemberConfirmNoChoiceKey = "remove.team.member.confirmation.no.choice.field"

    val firstnameRequiredGlobalKey        = "firstname.error.required.global"
    val firstnameMaxLengthGlobalKey       = "firstname.error.maxLength.global"
    val lastnameRequiredGlobalKey         = "lastname.error.required.global"
    val lastnameMaxLengthGlobalKey        = "lastname.error.maxLength.global"
    val emailaddressRequiredGlobalKey     = "emailaddress.error.required.global"
    val emailaddressNotValidGlobalKey     = "emailaddress.error.not.valid.global"
    val emailMaxLengthGlobalKey           = "emailaddress.error.maxLength.global"
    val passwordNotValidGlobalKey         = "password.error.not.valid.global"
    val passwordRequiredGlobalKey         = "password.error.required.global"
    val passwordNoMatchGlobalKey          = "password.error.no.match.global"
    val emailaddressAlreadyInUseGlobalKey = "emailaddress.already.registered.global"

    val accountUnverifiedGlobalKey             = "account.unverified.global"
    val accountLockedGlobalKey                 = "account.locked.global"
    val invalidCredentialsGlobalKey            = "invalid.credentials.global"
    val invalidPasswordGlobalKey               = "invalid.password.global"
    val currentPasswordRequiredGlobalKey       = "currentpassword.error.required.global"
    val currentPasswordInvalidGlobalKey        = "currentpassword.invalid.global"
    val redirectUriInvalidGlobalKey            = "redirect.uri.invalid.global"
    val privacyPolicyUrlInvalidGlobalKey       = "privacy.policy.url.invalid.global"
    val tNcUrlInvalidGlobalKey                 = "terms.conditions.url.invalid.global"
    val clientSecretLimitExceeded              = "client.secret.limit.exceeded"
    val productionCannotDeleteOnlyClientSecret = "production.cannot.delete.only.client.secret"
    val sandboxCannotDeleteOnlyClientSecret    = "sandbox.cannot.delete.only.client.secret"

    val deleteApplicationConfirmNoChoiceKey   = "delete.application.confirmation.no.choice.field"
    val deleteClientSecretsConfirmNoChoiceKey = "delete.client.secrets.confirmation.no.choice.field"
    val subscriptionConfirmationNoChoiceKey   = "subscription.confirmation.no.choice.field"
    val unsubscribeConfirmationNoChoiceKey    = "unsubscribe.confirmation.no.choice.field"
    val changeSubscriptionNoChoiceKey         = "subscription.change.no.choice.field"
    val accountDeleteConfirmationRequiredKey  = "developer.delete.error.required.field"
    val remove2SVConfirmNoChoiceKey           = "remove.2SV.confirmation.no.choice.field"

    val deleteRedirectConfirmationNoChoiceKey = "delete.redirect.confirmation.no.choice.field"

    val sellResellOrDistributeConfirmNoChoiceKey = "sell.resell.distribute.confirmation.no.choice.field"

    val verifyPasswordInvalidKey       = "verify.password.error.required.field"
    val verifyPasswordInvalidGlobalKey = "verify.password.error.required.global"

    val selectAClientSecretKey      = "select.client.secret.field"
    val selectFewerClientSecretsKey = "select.fewer.client.secrets.field"

    val accessCodeInvalidKey       = "accessCode.invalid.number.field"
    val accessCodeInvalidGlobalKey = "accessCode.invalid.number.global"

    val accessCodeErrorKey       = "accessCode.error.field"
    val accessCodeErrorGlobalKey = "accessCode.error.global"

    val selectMfaInvalidKey       = "selectMfa.invalid.mfaType.field"
    val selectMfaInvalidGlobalKey = "selectMfa.invalid.mfaType.global"

    val mfaNameChangeInvalidKey       = "mfaName.invalid.name.field"
    val mfaNameChangeInvalidGlobalKey = "mfaName.invalid.name.global"

    val mobileNumberInvalidKey       = "mobileNumber.invalid.number.field"
    val mobileNumberInvalidGlobalKey = "mobileNumber.invalid.number.global"

    val mobileNumberTooShortKey       = "mobileNumber.too.short.number.field"
    val mobileNumberTooShortGlobalKey = "mobileNumber.too.short.number.global"

    val selectedCategoryNonSelectedKey       = "error.selectedcategories.nonselected.field"
    val selectedCategoryNonSelectedGlobalKey = "error.selectedcategories.nonselected.global"

    val selectedApiRadioKey       = "error.select.apiradio.nonselected.field"
    val selectedApiRadioGlobalKey = "error.select.apiradio.nonselected.global"

    val selectedApisNonSelectedKey       = "error.selectedapis.nonselected.field"
    val selectedApisNonSelectedGlobalKey = "error.selectedapis.nonselected.global"

    val selectedTopicsNonSelectedKey       = "error.selectedtopics.nonselected.field"
    val selectedTopicsNonSelectedGlobalKey = "error.selectedtopics.nonselected.global"

    val responsibleIndividualFullnameRequiredKey     = "responsible_individual_fullname.error.required.field"
    val responsibleIndividualEmailAddressRequiredKey = "responsible_individual_emailaddress.error.required.field"

    val noApplicationsChoiceRequiredKey = "no.applications.choice.error.required.field"

    val formKeysMap: Map[String, String] = Map(
      firstnameRequiredKey           -> firstnameRequiredGlobalKey,
      firstnameMaxLengthKey          -> firstnameMaxLengthGlobalKey,
      lastnameRequiredKey            -> lastnameRequiredGlobalKey,
      lastnameMaxLengthKey           -> lastnameMaxLengthGlobalKey,
      emailaddressRequiredKey        -> emailaddressRequiredGlobalKey,
      emailaddressNotValidKey        -> emailaddressNotValidGlobalKey,
      emailMaxLengthKey              -> emailMaxLengthGlobalKey,
      emailalreadyInUseKey           -> emailaddressAlreadyInUseGlobalKey,
      passwordNotValidKey            -> passwordNotValidGlobalKey,
      passwordRequiredKey            -> passwordRequiredGlobalKey,
      passwordNoMatchKey             -> passwordNoMatchGlobalKey,
      accountUnverifiedKey           -> accountUnverifiedGlobalKey,
      invalidCredentialsKey          -> invalidCredentialsGlobalKey,
      invalidPasswordKey             -> invalidPasswordGlobalKey,
      accountLockedKey               -> accountLockedGlobalKey,
      currentPasswordRequiredKey     -> currentPasswordRequiredGlobalKey,
      currentPasswordInvalidKey      -> currentPasswordInvalidGlobalKey,
      redirectUriInvalidKey          -> redirectUriInvalidGlobalKey,
      privacyPolicyUrlInvalidKey     -> privacyPolicyUrlInvalidGlobalKey,
      tNcUrlInvalidKey               -> tNcUrlInvalidGlobalKey,
      termsOfUseAgreeKey             -> termsOfUseAgreeGlobalKey,
      accessCodeInvalidKey           -> accessCodeInvalidGlobalKey,
      accessCodeErrorKey             -> accessCodeErrorGlobalKey,
      selectedCategoryNonSelectedKey -> selectedCategoryNonSelectedGlobalKey,
      selectedApisNonSelectedKey     -> selectedApisNonSelectedGlobalKey,
      selectedApiRadioKey            -> selectedApiRadioGlobalKey,
      selectedTopicsNonSelectedKey   -> selectedTopicsNonSelectedGlobalKey,
      mobileNumberInvalidKey         -> mobileNumberInvalidGlobalKey,
      mobileNumberTooShortKey        -> mobileNumberTooShortGlobalKey
    )

    val globalKeys: Seq[String] = formKeysMap.values.toSeq

    val globalToField: Map[String, String] = Map(
      firstnameRequiredGlobalKey           -> firstnameField,
      firstnameMaxLengthGlobalKey          -> firstnameField,
      lastnameRequiredGlobalKey            -> lastnameField,
      lastnameMaxLengthGlobalKey           -> lastnameField,
      emailaddressRequiredGlobalKey        -> emailaddressField,
      emailaddressNotValidGlobalKey        -> emailaddressField,
      emailaddressAlreadyInUseGlobalKey    -> emailaddressField,
      passwordNotValidGlobalKey            -> passwordField,
      passwordRequiredGlobalKey            -> passwordField,
      passwordNoMatchGlobalKey             -> passwordField,
      accountLockedGlobalKey               -> currentPasswordField,
      emailaddressAlreadyInUseGlobalKey    -> emailaddressField,
      accountUnverifiedGlobalKey           -> emailaddressField,
      invalidCredentialsGlobalKey          -> emailaddressField,
      invalidPasswordGlobalKey             -> passwordField,
      currentPasswordRequiredGlobalKey     -> currentPasswordField,
      currentPasswordInvalidGlobalKey      -> currentPasswordField,
      emailMaxLengthGlobalKey              -> emailaddressField,
      accessCodeInvalidGlobalKey           -> accessCodeInvalidKey,
      selectedCategoryNonSelectedGlobalKey -> selectedCategoryNonSelectedKey,
      selectedApisNonSelectedGlobalKey     -> selectedApisNonSelectedKey,
      selectedApiRadioGlobalKey            -> selectedApiRadioKey,
      selectedTopicsNonSelectedGlobalKey   -> selectedTopicsNonSelectedKey,
      mobileNumberInvalidGlobalKey         -> mobileNumberInvalidKey,
      mobileNumberTooShortGlobalKey        -> mobileNumberTooShortKey
    )
  }

  import FormKeys._

  def firstnameValidator: Mapping[String] = textValidator(firstnameRequiredKey, firstnameMaxLengthKey)

  def lastnameValidator: Mapping[String] = textValidator(lastnameRequiredKey, lastnameMaxLengthKey)

  def fullnameValidator: Mapping[String] = textValidator(fullnameRequiredKey, fullnameMaxLengthKey, 100)

  def telephoneValidator: Mapping[String] = Forms.text.verifying(telephoneRequiredKey, telephone => telephone.length > 0)

  def commentsValidator: Mapping[String] = supportRequestValidator(commentsRequiredKey, commentsMaxLengthKey, 3000)

  def cidrBlockValidator: Mapping[String] = {
    val privateNetworkRanges = Set(
      new SubnetUtils("10.0.0.0/8"),
      new SubnetUtils("172.16.0.0/12"),
      new SubnetUtils("192.168.0.0/16")
    ) map { su =>
      su.setInclusiveHostCount(true)
      su.getInfo
    }

    def validateCidrBlock(cidrBlock: String): ValidationResult = {
      Try(new SubnetUtils(cidrBlock)) match {
        case Failure(_) => Invalid(Seq(ValidationError(ipAllowlistInvalidCidrBlockKey)))
        case _          =>
          val ipAndMask = cidrBlock.split("/")
          if (privateNetworkRanges.exists(_.isInRange(ipAndMask(0)))) Invalid(Seq(ValidationError(ipAllowlistPrivateCidrBlockKey)))
          else if (ipAndMask(1).toInt < 24) Invalid(Seq(ValidationError(ipAllowlistInvalidCidrBlockRangeKey)))
          else Valid
      }
    }

    Forms.text.verifying(Constraint[String](validateCidrBlock(_)))
  }

  def textValidator(requiredKey: String, maxLengthKey: String, maxLength: Int = 30): Mapping[String] =
    Forms.text.verifying(requiredKey, s => s.trim.length > 0).verifying(maxLengthKey, s => s.trim.length <= maxLength)

  def supportRequestValidator(requiredKey: String, maxLengthKey: String, maxLength: Int = 30): Mapping[String] = {
    val spambotCommentRegex = """(?i).*Como.+puedo.+iniciar.*""".r

    textValidator(requiredKey, maxLengthKey, maxLength)
      .verifying(commentsSpamKey, s => spambotCommentRegex.findFirstMatchIn(s).isEmpty)
  }

  def emailValidator(emailRequiredMessage: String = emailaddressRequiredKey, maxLength: Int = 320): Mapping[String] = {

    Forms.text
      .verifying(emailaddressNotValidKey, email => EmailAddress.isValid(email) || email.length == 0)
      .verifying(emailMaxLengthKey, email => email.length <= maxLength)
      .verifying(emailRequiredMessage, email => email.length > 0)
  }

  def loginPasswordValidator: Mapping[String] =
    Forms.text.verifying(loginPasswordRequiredKey, isNotBlankString)

  def currentPasswordValidator: Mapping[String] =
    Forms.text.verifying(currentPasswordRequiredKey, isNotBlankString)

  def passwordValidator: Mapping[String] = {
    val passwordRegex = """(?=^.{12,}$)(?=.*\d)(?=.*[ !"#\$%&'()\*\+,-\./:;<=>\?@\[\\\]\^_`\{\|\}~]+)(?=.*[A-Z])(?=.*[a-z]).*$""".r

    Forms.text
      .verifying(passwordNotValidKey, s => passwordRegex.findFirstMatchIn(s).exists(_ => true))
      .verifying(passwordRequiredKey, isNotBlankString)
  }

  def passwordsMatch: Constraint[ConfirmPassword] = Constraint[ConfirmPassword](passwordNoMatchKey) {
    case rf if rf.password != rf.confirmPassword => Invalid(ValidationError(passwordNoMatchGlobalKey))
    case _                                       => Valid
  }

  def redirectUriValidator: Mapping[String] = Forms.text.verifying(redirectUriInvalidKey, s => RedirectUri(s).isDefined)

  def privacyPolicyUrlValidator: Mapping[String] = Forms.text.verifying(privacyPolicyUrlInvalidKey, s => isBlank(s) || isValidUrl(s))

  def tNcUrlValidator: Mapping[String] = Forms.text.verifying(tNcUrlInvalidKey, s => isBlank(s) || isValidUrl(s))

  def applicationNameValidator: Mapping[String] = {
    def isAcceptedAscii(s: String) = {
      !s.toCharArray.exists(c => 32 > c || c > 126)
    }
    // This does 1 & 2 above
    Forms.text.verifying(applicationNameInvalidKeyLengthAndCharacters, s => s.length >= 2 && s.length <= 50 && isAcceptedAscii(s))
  }

  def environmentValidator: Mapping[Option[String]] = optional(text).verifying(environmentInvalidKey, s => s.fold(false)(isValidEnvironment))

  private def isNotBlankString: String => Boolean = s => s.trim.length > 0

  private def isBlank: String => Boolean = s => s.length == 0

  def isValidUrl: String => Boolean = s => Try(new URL(s.trim)).isSuccess

  private def isValidEnvironment(s: String) = Environment.apply(s).isDefined
}
