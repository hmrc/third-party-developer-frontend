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

package uk.gov.hmrc.thirdpartydeveloperfrontend.utils

import java.time.Period
import java.util.UUID.randomUUID
import scala.util.Random

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.{Access, _}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.ApplicationStateHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

trait TestApplications extends FixedClock with ApplicationStateHelper {
  self: CollaboratorTracker =>

  private def randomString(length: Int) = Random.alphanumeric.take(length).mkString

  def aSandboxApplication(
      appId: ApplicationId = ApplicationId.random,
      clientId: ClientId = ClientId(randomString(28)),
      adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail,
      developerEmail: LaxEmailAddress = "developer@example.com".toLaxEmail
    ): Application = {

    anApplication(
      appId,
      clientId,
      environment = Environment.SANDBOX,
      state = InState.production("a", "b", "c"),
      adminEmail = adminEmail,
      developerEmail = developerEmail
    )
  }

  def anApplication(
      appId: ApplicationId = ApplicationId.random,
      clientId: ClientId = ClientId(randomString(28)),
      grantLength: Period = Period.ofDays(547),
      environment: Environment = Environment.PRODUCTION,
      state: ApplicationState = InState.production("test@test.com", "test name", "test"),
      adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail,
      developerEmail: LaxEmailAddress = "developer@example.com".toLaxEmail,
      access: Access = standardAccess(),
      ipAllowlist: IpAllowlist = IpAllowlist()
    ): Application = {

    Application(
      id = appId,
      clientId = clientId,
      name = "App name 1",
      createdOn = instant,
      lastAccess = Some(instant),
      grantLength = Period.ofDays(547),
      deployedTo = environment,
      description = Some("Description 1"),
      collaborators = Set(adminEmail.asAdministratorCollaborator, developerEmail.asDeveloperCollaborator),
      state = state,
      access = access,
      ipAllowlist = ipAllowlist
    )
  }

  val aStandardApplication: Application = anApplication()

  def aStandardApprovedApplication: Application = aStandardApplication

  def aStandardNonApprovedApplication(adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail): Application =
    anApplication(adminEmail = adminEmail).withState(InState.testing)

  def aStandardPendingApprovalApplication(adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail): Application =
    anApplication(adminEmail = adminEmail).withState(InState.pendingRequesterVerification("test@test.com", "test name", "test"))

  def aStandardPendingResponsibleIndividualVerificationApplication(adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail): Application =
    anApplication(adminEmail = adminEmail).withState(InState.pendingResponsibleIndividualVerification("admin@example.com", "admin name"))

  def standardAccess(
      redirectUris: List[RedirectUri] = List("https://redirect1", "https://redirect2").map(RedirectUri.unsafeApply(_)),
      termsAndConditionsUrl: Option[String] = Some("http://example.com/terms"),
      privacyPolicyUrl: Option[String] = Some("http://example.com/privacy")
    ): Access.Standard = {

    Access.Standard(redirectUris, termsAndConditionsUrl, privacyPolicyUrl)
  }

  def anROPCApplication(): Application = anApplication(access = ropcAccess())

  def ropcAccess(scopes: Set[String] = Set(randomString(10), randomString(10), randomString(10))): Access = Access.Ropc(scopes)

  def aPrivilegedApplication(): Application = anApplication(access = privilegedAccess())

  def privilegedAccess(scopes: Set[String] = Set(randomString(10), randomString(10), randomString(10))): Access.Privileged = Access.Privileged(None, scopes)

  def tokens(clientId: ClientId = ClientId(randomString(28)), clientSecret: String = randomString(28), accessToken: String = randomString(28)): ApplicationToken = {
    ApplicationToken(List(aClientSecret()), accessToken)
  }

  private def aClientSecret() = ClientSecretResponse(ClientSecret.Id.random, randomUUID.toString, instant)

  implicit class AppAugment(val app: Application) {
    final def withName(name: String): Application = app.copy(name = name)

    final def withDescription(description: Option[String]): Application = app.copy(description = description)

    final def withTeamMember(email: LaxEmailAddress, userRole: Collaborator.Role): Application =
      app.copy(collaborators = app.collaborators + Collaborator(email, userRole, UserId.random))

    final def withTeamMembers(teamMembers: Set[Collaborator]): Application = app.copy(collaborators = teamMembers)

    final def withState(state: ApplicationState): Application = app.copy(state = state)

    final def withCheckInformation(checkInformation: CheckInformation): Application = app.copy(checkInformation = Some(checkInformation))

    def standardAccess: Access.Standard = {
      if (app.access.accessType != AccessType.STANDARD) {
        throw new IllegalArgumentException(s"You can only use this method on a Standard application. Your app was ${app.access.accessType}")
      } else {
        app.access.asInstanceOf[Access.Standard]
      }
    }

    final def withRedirectUris(someRedirectUris: List[RedirectUri]): Application = app.copy(access = standardAccess.copy(redirectUris = someRedirectUris))

    final def withTermsAndConditionsUrl(url: Option[String]): Application = app.copy(access = standardAccess.copy(termsAndConditionsUrl = url))

    final def withPrivacyPolicyUrl(url: Option[String]): Application = app.copy(access = standardAccess.copy(privacyPolicyUrl = url))
  }
}
