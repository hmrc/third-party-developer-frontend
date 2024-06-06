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

  case class FieldNameKey(value: String)     extends AnyVal { override def toString(): String = value }
  case class GlobalMessageKey(value: String) extends AnyVal { override def toString(): String = value }
  case class FieldMessageKey(value: String)  extends AnyVal { override def toString(): String = value }

  object Conversions {
    implicit def fromFieldNameKeyToString(in: FieldNameKey): String         = in.value
    implicit def fromGlobalMessageKeyToString(in: GlobalMessageKey): String = in.value
    implicit def fromFieldMessageKeyToString(in: FieldMessageKey): String   = in.value
  }

  object FormKeys {

    val firstnameField               = FieldNameKey("firstname")
    val lastnameField                = FieldNameKey("lastname")
    val fullnameField                = FieldNameKey("fullname")
    val emailaddressField            = FieldNameKey("emailaddress")
    val passwordField                = FieldNameKey("password")
    val loginPasswordField           = FieldNameKey("loginpassword")
    val currentPasswordField         = FieldNameKey("currentpassword")
    val confirmapasswordField        = FieldNameKey("confirmpassword")
    val appNameField                 = FieldNameKey("applicationName")
    val appDescriptionField          = FieldNameKey("description")
    val deleteSelectField            = FieldNameKey("deleteSelect")
    val selectedApisNonSelectedField = FieldNameKey("errorSelectedApisNonselectedField")

    val firstnameRequiredKey                = FieldMessageKey("firstname.error.required.field")
    val firstnameMaxLengthKey               = FieldMessageKey("firstname.error.maxLength.field")
    val lastnameRequiredKey                 = FieldMessageKey("lastname.error.required.field")
    val lastnameMaxLengthKey                = FieldMessageKey("lastname.error.maxLength.field")
    val fullnameRequiredKey                 = FieldMessageKey("fullname.error.required.field")
    val fullnameMaxLengthKey                = FieldMessageKey("fullname.error.maxLength.field")
    val commentsRequiredKey                 = FieldMessageKey("comments.error.required.field")
    val commentsMaxLengthKey                = FieldMessageKey("comments.error.maxLength.field")
    val commentsSpamKey                     = FieldMessageKey("comments.error.spam.field")
    val ipAllowlistAddAnotherNoChoiceKey    = FieldMessageKey("ipAllowlist.addAnother.confirmation.no.choice.field")
    val ipAllowlistInvalidCidrBlockKey      = FieldMessageKey("ipAllowlist.cidrBlock.invalid")
    val ipAllowlistPrivateCidrBlockKey      = FieldMessageKey("ipAllowlist.cidrBlock.invalid.private")
    val ipAllowlistInvalidCidrBlockRangeKey = FieldMessageKey("ipAllowlist.cidrBlock.invalid.range")
    val telephoneRequiredKey                = FieldMessageKey("telephone.error.required.field")
    val emailaddressRequiredKey             = FieldMessageKey("emailaddress.error.required.field")
    val emailaddressNotValidKey             = FieldMessageKey("emailaddress.error.not.valid.field")
    val emailMaxLengthKey                   = FieldMessageKey("emailaddress.error.maxLength.field")
    val detailsRequiredKey                  = FieldMessageKey("details.error.required.field")
    val detailsMaxLengthKey                 = FieldMessageKey("details.error.maxLength.field")

    val termsOfUseAgreeKey       = FieldMessageKey("termsofuse.error.required.field")
    val termsOfUseAgreeGlobalKey = GlobalMessageKey("termsofuse.error.required.global")

    val passwordNotValidKey = FieldMessageKey("password.error.not.valid.field")
    val passwordRequiredKey = FieldMessageKey("password.error.required.field")
    val passwordNoMatchKey  = FieldMessageKey("password.error.no.match.field")

    val loginPasswordRequiredKey = FieldMessageKey("loginpassword.error.required.field")

    val emailalreadyInUseKey  = FieldMessageKey("emailaddress.already.registered.field")
    val emailalreadyInUse2Key = FieldMessageKey("emailaddress.already.registered.2.field")

    val accountUnverifiedKey        = FieldMessageKey("account.unverified.field")
    val invalidCredentialsKey       = FieldMessageKey("invalid.credentials.field")
    val invalidPasswordKey          = FieldMessageKey("invalid.password.field")
    val accountLockedKey            = FieldMessageKey("account.locked.field")
    val accountLocked2Key           = FieldMessageKey("account.locked.2.field")
    val currentPasswordRequiredKey  = FieldMessageKey("currentpassword.error.required.field")
    val currentPasswordInvalidKey   = FieldMessageKey("currentpassword.invalid.field")
    val redirectUriInvalidKey       = FieldMessageKey("redirect.uri.invalid.field")
    val privacyPolicyUrlRequiredKey = FieldMessageKey("privacy.policy.url.required.field")
    val privacyPolicyUrlInvalidKey  = FieldMessageKey("privacy.policy.url.invalid.field")
    val privacyPolicyUrlNoChoiceKey = FieldMessageKey("privacy.policy.url.no.choice.field")
    val tNcUrlInvalidKey            = FieldMessageKey("terms.conditions.url.invalid.field")
    val tNcUrlNoChoiceKey           = FieldMessageKey("terms.conditions.url.no.choice.field")
    val tNcUrlRequiredKey           = FieldMessageKey("terms.conditions.url.required.field")

    val applicationNameInvalidKeyLengthAndCharacters = "application.name.invalid.length.and.characters"

    val applicationNameInvalidKey       = FieldMessageKey("application.name.invalid.name")
    val applicationNameAlreadyExistsKey = FieldMessageKey("application.name.already.exists.field")

    val environmentInvalidKey = FieldMessageKey("environment.error.required.field")

    val teamMemberEmailRequired = "team.member.error.emailAddress.required.field"
    val teamMemberAlreadyExists = "team.member.error.emailAddress.already.exists.field"

    val teamMemberRoleRequired             = "roles.error.answer.required.field.content"
    val removeTeamMemberConfirmNoChoiceKey = FieldMessageKey("remove.team.member.confirmation.no.choice.field")

    val firstnameRequiredGlobalKey        = GlobalMessageKey("firstname.error.required.global")
    val firstnameMaxLengthGlobalKey       = GlobalMessageKey("firstname.error.maxLength.global")
    val lastnameRequiredGlobalKey         = GlobalMessageKey("lastname.error.required.global")
    val lastnameMaxLengthGlobalKey        = GlobalMessageKey("lastname.error.maxLength.global")
    val emailaddressRequiredGlobalKey     = GlobalMessageKey("emailaddress.error.required.global")
    val emailaddressNotValidGlobalKey     = GlobalMessageKey("emailaddress.error.not.valid.global")
    val emailMaxLengthGlobalKey           = GlobalMessageKey("emailaddress.error.maxLength.global")
    val passwordNotValidGlobalKey         = GlobalMessageKey("password.error.not.valid.global")
    val passwordRequiredGlobalKey         = GlobalMessageKey("password.error.required.global")
    val passwordNoMatchGlobalKey          = GlobalMessageKey("password.error.no.match.global")
    val emailaddressAlreadyInUseGlobalKey = GlobalMessageKey("emailaddress.already.registered.global")

    val accountUnverifiedGlobalKey             = GlobalMessageKey("account.unverified.global")
    val accountLockedGlobalKey                 = GlobalMessageKey("account.locked.global")
    val invalidCredentialsGlobalKey            = GlobalMessageKey("invalid.credentials.global")
    val invalidPasswordGlobalKey               = GlobalMessageKey("invalid.password.global")
    val currentPasswordRequiredGlobalKey       = GlobalMessageKey("currentpassword.error.required.global")
    val currentPasswordInvalidGlobalKey        = GlobalMessageKey("currentpassword.invalid.global")
    val redirectUriInvalidGlobalKey            = GlobalMessageKey("redirect.uri.invalid.global")
    val privacyPolicyUrlInvalidGlobalKey       = GlobalMessageKey("privacy.policy.url.invalid.global")
    val tNcUrlInvalidGlobalKey                 = GlobalMessageKey("terms.conditions.url.invalid.global")
    val clientSecretLimitExceeded              = "client.secret.limit.exceeded"
    val productionCannotDeleteOnlyClientSecret = "production.cannot.delete.only.client.secret"
    val sandboxCannotDeleteOnlyClientSecret    = "sandbox.cannot.delete.only.client.secret"

    val deleteApplicationConfirmNoChoiceKey   = FieldMessageKey("delete.application.confirmation.no.choice.field")
    val deleteClientSecretsConfirmNoChoiceKey = FieldMessageKey("delete.client.secrets.confirmation.no.choice.field")
    val subscriptionConfirmationNoChoiceKey   = FieldMessageKey("subscription.confirmation.no.choice.field")
    val unsubscribeConfirmationNoChoiceKey    = FieldMessageKey("unsubscribe.confirmation.no.choice.field")
    val changeSubscriptionNoChoiceKey         = FieldMessageKey("subscription.change.no.choice.field")
    val accountDeleteConfirmationRequiredKey  = FieldMessageKey("developer.delete.error.required.field")
    val remove2SVConfirmNoChoiceKey           = FieldMessageKey("remove.2SV.confirmation.no.choice.field")

    val deleteRedirectConfirmationNoChoiceKey = FieldMessageKey("delete.redirect.confirmation.no.choice.field")

    val sellResellOrDistributeConfirmNoChoiceKey = FieldMessageKey("sell.resell.distribute.confirmation.no.choice.field")

    val verifyPasswordInvalidKey       = FieldMessageKey("verify.password.error.required.field")
    val verifyPasswordInvalidGlobalKey = GlobalMessageKey("verify.password.error.required.global")

    val selectAClientSecretKey      = FieldMessageKey("select.client.secret.field")
    val selectFewerClientSecretsKey = FieldMessageKey("select.fewer.client.secrets.field")

    val accessCodeInvalidKey       = FieldMessageKey("accessCode.invalid.number.field")
    val accessCodeInvalidGlobalKey = GlobalMessageKey("accessCode.invalid.number.global")

    val accessCodeErrorKey       = FieldMessageKey("accessCode.error.field")
    val accessCodeErrorGlobalKey = GlobalMessageKey("accessCode.error.global")

    val selectMfaInvalidKey       = FieldMessageKey("selectMfa.invalid.mfaType.field")
    val selectMfaInvalidGlobalKey = GlobalMessageKey("selectMfa.invalid.mfaType.global")

    val mfaNameChangeInvalidKey       = FieldMessageKey("mfaName.invalid.name.field")
    val mfaNameChangeInvalidGlobalKey = GlobalMessageKey("mfaName.invalid.name.global")

    val mobileNumberInvalidKey       = FieldMessageKey("mobileNumber.invalid.number.field")
    val mobileNumberInvalidGlobalKey = GlobalMessageKey("mobileNumber.invalid.number.global")

    val mobileNumberTooShortKey       = FieldMessageKey("mobileNumber.too.short.number.field")
    val mobileNumberTooShortGlobalKey = GlobalMessageKey("mobileNumber.too.short.number.global")

    val selectedCategoryNonSelectedKey       = FieldMessageKey("error.selectedcategories.nonselected.field")
    val selectedCategoryNonSelectedGlobalKey = GlobalMessageKey("error.selectedcategories.nonselected.global")

    val selectedApiRadioKey       = FieldMessageKey("error.select.apiradio.nonselected.field")
    val selectedApiRadioGlobalKey = GlobalMessageKey("error.select.apiradio.nonselected.global")

    val selectedApisNonSelectedKey       = FieldMessageKey("error.selectedapis.nonselected.field")
    val selectedApisNonSelectedGlobalKey = GlobalMessageKey("error.selectedapis.nonselected.global")

    val selectedTopicsNonSelectedKey       = FieldMessageKey("error.selectedtopics.nonselected.field")
    val selectedTopicsNonSelectedGlobalKey = GlobalMessageKey("error.selectedtopics.nonselected.global")

    val responsibleIndividualFullnameRequiredKey     = FieldMessageKey("responsible_individual_fullname.error.required.field")
    val responsibleIndividualEmailAddressRequiredKey = FieldMessageKey("responsible_individual_emailaddress.error.required.field")

    val noApplicationsChoiceRequiredKey = FieldMessageKey("no.applications.choice.error.required.field")

    val supportDetailsRequiredKey        = FieldMessageKey("support.details.error.required.field")
    val supportDetailsMaxLengthKey       = FieldMessageKey("support.details.error.maxLength.field")
    val supportDetailsRequiredGlobalKey  = GlobalMessageKey("support.details.error.required.global")
    val supportDetailsMaxLengthGlobalKey = GlobalMessageKey("support.details.error.maxLength.global")

    val supportEnquiryIntialChoiceRequiredKey       = FieldMessageKey("support.enquiry.initialchoice.error.required.field")
    val supportEnquiryIntialChoiceRequiredGlobalKey = GlobalMessageKey("support.enquiry.initialchoice.error.required.global")

    val supportChoseAPrivateApiNameRequiredKey       = FieldMessageKey("support.chooseaprivateapiname.error.required.field")
    val supportChoseAPrivateApiNameRequiredGlobalKey = GlobalMessageKey("support.chooseaprivateapiname.error.required.global")

    val supportSigningInChoiceRequiredKey       = FieldMessageKey("support.signinginchoice.error.required.field")
    val supportSigningInChoiceRequiredGlobalKey = GlobalMessageKey("support.signinginchoice.error.required.global")

    val supportHelpUsingApiChoiceRequiredKey       = FieldMessageKey("support.helpusingapichoice.error.required.field")
    val supportHelpUsingApiChoiceRequiredGlobalKey = GlobalMessageKey("support.helpusingapichoice.error.required.global")

    val formKeysMap: Map[FieldMessageKey, GlobalMessageKey] = Map(
      supportHelpUsingApiChoiceRequiredKey   -> supportHelpUsingApiChoiceRequiredGlobalKey,
      supportChoseAPrivateApiNameRequiredKey -> supportChoseAPrivateApiNameRequiredGlobalKey,
      supportDetailsRequiredKey              -> supportDetailsRequiredGlobalKey,
      supportDetailsMaxLengthKey             -> supportDetailsMaxLengthGlobalKey,
      firstnameRequiredKey                   -> firstnameRequiredGlobalKey,
      firstnameMaxLengthKey                  -> firstnameMaxLengthGlobalKey,
      lastnameRequiredKey                    -> lastnameRequiredGlobalKey,
      lastnameMaxLengthKey                   -> lastnameMaxLengthGlobalKey,
      emailaddressRequiredKey                -> emailaddressRequiredGlobalKey,
      emailaddressNotValidKey                -> emailaddressNotValidGlobalKey,
      emailMaxLengthKey                      -> emailMaxLengthGlobalKey,
      emailalreadyInUseKey                   -> emailaddressAlreadyInUseGlobalKey,
      passwordNotValidKey                    -> passwordNotValidGlobalKey,
      passwordRequiredKey                    -> passwordRequiredGlobalKey,
      passwordNoMatchKey                     -> passwordNoMatchGlobalKey,
      accountUnverifiedKey                   -> accountUnverifiedGlobalKey,
      invalidCredentialsKey                  -> invalidCredentialsGlobalKey,
      invalidPasswordKey                     -> invalidPasswordGlobalKey,
      accountLockedKey                       -> accountLockedGlobalKey,
      currentPasswordRequiredKey             -> currentPasswordRequiredGlobalKey,
      currentPasswordInvalidKey              -> currentPasswordInvalidGlobalKey,
      redirectUriInvalidKey                  -> redirectUriInvalidGlobalKey,
      privacyPolicyUrlInvalidKey             -> privacyPolicyUrlInvalidGlobalKey,
      tNcUrlInvalidKey                       -> tNcUrlInvalidGlobalKey,
      termsOfUseAgreeKey                     -> termsOfUseAgreeGlobalKey,
      accessCodeInvalidKey                   -> accessCodeInvalidGlobalKey,
      accessCodeErrorKey                     -> accessCodeErrorGlobalKey,
      selectedCategoryNonSelectedKey         -> selectedCategoryNonSelectedGlobalKey,
      selectedApisNonSelectedKey             -> selectedApisNonSelectedGlobalKey,
      selectedApiRadioKey                    -> selectedApiRadioGlobalKey,
      selectedTopicsNonSelectedKey           -> selectedTopicsNonSelectedGlobalKey,
      mobileNumberInvalidKey                 -> mobileNumberInvalidGlobalKey,
      mobileNumberTooShortKey                -> mobileNumberTooShortGlobalKey
    )

    def findFieldKeys(rawMessage: String): Option[(FieldMessageKey, GlobalMessageKey)] = {
      formKeysMap.find(_._1.value == rawMessage)
    }

    val globalKeys: Seq[GlobalMessageKey] = formKeysMap.values.toSeq

    val globalToField: Map[GlobalMessageKey, FieldNameKey] = Map(
      firstnameRequiredGlobalKey        -> firstnameField,
      firstnameMaxLengthGlobalKey       -> firstnameField,
      lastnameRequiredGlobalKey         -> lastnameField,
      lastnameMaxLengthGlobalKey        -> lastnameField,
      emailaddressRequiredGlobalKey     -> emailaddressField,
      emailaddressNotValidGlobalKey     -> emailaddressField,
      emailaddressAlreadyInUseGlobalKey -> emailaddressField,
      passwordNotValidGlobalKey         -> passwordField,
      passwordRequiredGlobalKey         -> passwordField,
      passwordNoMatchGlobalKey          -> passwordField,
      accountLockedGlobalKey            -> currentPasswordField,
      emailaddressAlreadyInUseGlobalKey -> emailaddressField,
      accountUnverifiedGlobalKey        -> emailaddressField,
      invalidCredentialsGlobalKey       -> emailaddressField,
      invalidPasswordGlobalKey          -> passwordField,
      currentPasswordRequiredGlobalKey  -> currentPasswordField,
      currentPasswordInvalidGlobalKey   -> currentPasswordField,
      emailMaxLengthGlobalKey           -> emailaddressField,
      selectedApisNonSelectedGlobalKey  -> selectedApisNonSelectedField
    )
  }

  import FormKeys._

  import Conversions._

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

  def textValidator(requiredFieldMessageKey: FieldMessageKey, maxLengthKey: FieldMessageKey, maxLength: Int = 30): Mapping[String] =
    Forms.text.verifying(requiredFieldMessageKey, s => s.trim.length > 0).verifying(maxLengthKey, s => s.trim.length <= maxLength)

  def supportRequestValidator(requiredFieldMessageKey: FieldMessageKey, maxLengthKey: FieldMessageKey, maxLength: Int = 30): Mapping[String] = {
    val spambotCommentRegex = """(?i).*Como.+puedo.+iniciar.*""".r

    textValidator(requiredFieldMessageKey, maxLengthKey, maxLength)
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
    case rf if rf.password != rf.confirmPassword => Invalid(ValidationError(passwordNoMatchGlobalKey.value))
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
