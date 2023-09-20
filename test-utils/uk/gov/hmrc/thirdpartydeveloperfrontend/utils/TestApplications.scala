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

import java.time.{LocalDateTime, Period, ZoneOffset}
import java.util.UUID.randomUUID
import scala.util.Random

import uk.gov.hmrc.apiplatform.modules.applications.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.AccessType
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

trait TestApplications {
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
      state = ApplicationState(State.PRODUCTION, None, None),
      adminEmail = adminEmail,
      developerEmail = developerEmail
    )
  }

  def anApplication(
      appId: ApplicationId = ApplicationId.random,
      clientId: ClientId = ClientId(randomString(28)),
      grantLength: Period = Period.ofDays(547),
      environment: Environment = Environment.PRODUCTION,
      state: ApplicationState = ApplicationState.production("test@test.com", "test name", "test"),
      adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail,
      developerEmail: LaxEmailAddress = "developer@example.com".toLaxEmail,
      access: Access = standardAccess(),
      ipAllowlist: IpAllowlist = IpAllowlist()
    ): Application = {

    Application(
      id = appId,
      clientId = clientId,
      name = "App name 1",
      createdOn = LocalDateTime.now(ZoneOffset.UTC),
      lastAccess = Some(LocalDateTime.now(ZoneOffset.UTC)),
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
    anApplication(adminEmail = adminEmail).withState(ApplicationState.testing)

  def aStandardPendingApprovalApplication(adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail): Application =
    anApplication(adminEmail = adminEmail).withState(ApplicationState.pendingRequesterVerification("test@test.com", "test name", "test"))

  def aStandardPendingResponsibleIndividualVerificationApplication(adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail): Application =
    anApplication(adminEmail = adminEmail).withState(ApplicationState.pendingResponsibleIndividualVerification("admin@example.com", "admin name"))

  def standardAccess(
      redirectUris: List[String] = List("https://redirect1", "https://redirect2"),
      termsAndConditionsUrl: Option[String] = Some("http://example.com/terms"),
      privacyPolicyUrl: Option[String] = Some("http://example.com/privacy")
    ): Standard = {

    Standard(redirectUris, termsAndConditionsUrl, privacyPolicyUrl)
  }

  def anROPCApplication(): Application = anApplication(access = ropcAccess())

  def ropcAccess(scopes: Set[String] = Set(randomString(10), randomString(10), randomString(10))): Access = ROPC(scopes)

  def aPrivilegedApplication(): Application = anApplication(access = privilegedAccess())

  def privilegedAccess(scopes: Set[String] = Set(randomString(10), randomString(10), randomString(10))): Privileged = Privileged(scopes)

  def tokens(clientId: ClientId = ClientId(randomString(28)), clientSecret: String = randomString(28), accessToken: String = randomString(28)): ApplicationToken = {
    ApplicationToken(List(aClientSecret()), accessToken)
  }

  private def aClientSecret() = ClientSecretResponse(ClientSecret.Id.random, randomUUID.toString, LocalDateTime.now())

  implicit class AppAugment(val app: Application) {
    final def withName(name: String): Application = app.copy(name = name)

    final def withDescription(description: Option[String]): Application = app.copy(description = description)

    final def withTeamMember(email: LaxEmailAddress, userRole: Collaborator.Role): Application =
      app.copy(collaborators = app.collaborators + Collaborator(email, userRole, UserId.random))

    final def withTeamMembers(teamMembers: Set[Collaborator]): Application = app.copy(collaborators = teamMembers)

    final def withState(state: ApplicationState): Application = app.copy(state = state)

    final def withCheckInformation(checkInformation: CheckInformation): Application = app.copy(checkInformation = Some(checkInformation))

    def standardAccess: Standard = {
      if (app.access.accessType != AccessType.STANDARD) {
        throw new IllegalArgumentException(s"You can only use this method on a Standard application. Your app was ${app.access.accessType}")
      } else {
        app.access.asInstanceOf[Standard]
      }
    }

    final def withRedirectUris(redirectUris: List[RedirectUri]): Application = app.copy(access = standardAccess.copy(redirectUris = redirectUris.map(_.uri)))

    final def withTermsAndConditionsUrl(url: Option[String]): Application = app.copy(access = standardAccess.copy(termsAndConditionsUrl = url))

    final def withPrivacyPolicyUrl(url: Option[String]): Application = app.copy(access = standardAccess.copy(privacyPolicyUrl = url))
  }
}
