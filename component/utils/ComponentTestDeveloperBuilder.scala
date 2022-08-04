package utils

import uk.gov.hmrc.apiplatform.modules.mfa.models.{AuthenticatorAppMfaDetailSummary, MfaDetail, MfaId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences

import java.time.LocalDateTime
import java.util.UUID

trait ComponentTestDeveloperBuilder {
  val staticUserId= UserId(UUID.fromString("11edcde7-c619-4bc1-bb6a-84dc14ea25cd"))
  val authenticatorAppMfaDetails = AuthenticatorAppMfaDetailSummary(MfaId.random, "name", LocalDateTime.now, verified = true)

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
