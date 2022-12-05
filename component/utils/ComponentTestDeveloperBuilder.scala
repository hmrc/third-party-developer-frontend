package utils

import uk.gov.hmrc.apiplatform.modules.mfa.models.{AuthenticatorAppMfaDetailSummary, MfaDetail, MfaId, SmsMfaDetailSummary}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences

import java.time.LocalDateTime
import java.util.UUID

trait ComponentTestDeveloperBuilder {
  val staticUserId= UserId(UUID.fromString("11edcde7-c619-4bc1-bb6a-84dc14ea25cd"))
  val authenticatorAppMfaDetails = AuthenticatorAppMfaDetailSummary(MfaId(UUID.fromString("13eae037-7b76-4bfd-8f77-feebd0611ebb")), "name", LocalDateTime.now, verified = true)
  val smsMfaDetails = SmsMfaDetailSummary(MfaId(UUID.fromString("6a3b98f1-a2c0-488b-bf0b-cfc86ccfe24d")), "name", LocalDateTime.now, "+447890123456", verified = true)

  def buildDeveloper( emailAddress: String = "something@example.com",
                      firstName: String = "John",
                      lastName: String = "Doe",
                      organisation: Option[String] = None,
                      mfaDetails: List[MfaDetail] = List.empty,
                      emailPreferences: EmailPreferences = EmailPreferences.noPreferences
                    ): Developer = {
    Developer(
      staticUserId,
      emailAddress,
      firstName,
      lastName,
      organisation,
      mfaDetails,
      emailPreferences
    )
  }
}
