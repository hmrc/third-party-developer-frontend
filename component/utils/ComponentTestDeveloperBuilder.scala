package utils

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences

import java.util.UUID

trait ComponentTestDeveloperBuilder {
  val staticUserUUID = UUID.fromString("11edcde7-c619-4bc1-bb6a-84dc14ea25cd")
  val staticUserId= UserId(staticUserUUID)
  def buildDeveloper(
                      emailAddress: String = "something@example.com",
                      firstName: String = "John",
                      lastName: String = "Doe",
                      organisation: Option[String] = None,
                      mfaEnabled: Option[Boolean] = None,
                      emailPreferences: EmailPreferences = EmailPreferences.noPreferences
                    ): Developer = {
    Developer(
      staticUserId,
      emailAddress,
      firstName,
      lastName,
      organisation,
      mfaEnabled,
      emailPreferences
    )
  }
}

object DeveloperBuilder {

}
