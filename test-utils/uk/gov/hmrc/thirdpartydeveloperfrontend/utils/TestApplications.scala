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

import java.util.UUID.randomUUID
import scala.util.Random

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.ApplicationStateHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

trait TestApplications extends FixedClock with ApplicationStateHelper with ApplicationWithCollaboratorsFixtures {
  self: CollaboratorTracker =>

  private def randomString(length: Int) = Random.alphanumeric.take(length).mkString

  def aSandboxApplication(
      // appId: ApplicationId = ApplicationId.random,
      // clientId: ClientId = ClientId(randomString(28)),
      adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail,
      developerEmail: LaxEmailAddress = "developer@example.com".toLaxEmail
    ): ApplicationWithCollaborators = {

    anApplication(
      // appId,
      // clientId,
      environment = Environment.SANDBOX,
      state = InState.production("a", "b", "c"),
      adminEmail = adminEmail,
      developerEmail = developerEmail
    )
  }

  def anApplication(
      // appId: ApplicationId = ApplicationId.random,
      // clientId: ClientId = ClientId(randomString(28)),
      // grantLength: Period = Period.ofDays(547),
      environment: Environment = Environment.PRODUCTION,
      state: ApplicationState = InState.production("test@test.com", "test name", "test"),
      adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail,
      developerEmail: LaxEmailAddress = "developer@example.com".toLaxEmail,
      access: Access = standardAccess(),
    ): ApplicationWithCollaborators = {

    /*

  lazy val adminDeveloper = buildTrackedUser("admin@example.com".toLaxEmail, "firstName1", "lastName1")
  lazy val JoeBloggs      = buildTrackedUser("developer@example.com".toLaxEmail, "Joe", "Bloggs")
  val standardDeveloper   = buildTrackedUser("developer@example.com".toLaxEmail, "firstName2", "lastName2")
     */

    standardApp
      .withEnvironment(environment)
      .withState(state)
      .withAccess(access)
      .withCollaborators(adminEmail.asAdministratorCollaborator, developerEmail.asDeveloperCollaborator)
      .withDescription(Some("Description 1"))
    // ApplicationWithCollaborators(
    //   CoreApplication(
    //     id = appId,
    //     clientId = clientId,
    //     name = "App name 1",
    //     createdOn = instant,
    //     lastAccess = Some(instant),
    //     grantLength = Period.ofDays(547),
    //     deployedTo = environment,
    //     description = Some("Description 1"),
    //     state = state,
    //     access = access
    //   ),
    //   collaborators = Set(adminEmail.asAdministratorCollaborator, developerEmail.asDeveloperCollaborator)
    // )
  }

  lazy val aStandardApplication: ApplicationWithCollaborators = anApplication()

  def aStandardApprovedApplication: ApplicationWithCollaborators = aStandardApplication

  def aStandardNonApprovedApplication(adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail): ApplicationWithCollaborators =
    anApplication(adminEmail = adminEmail).withState(InState.testing)

  def aStandardPendingApprovalApplication(adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail): ApplicationWithCollaborators =
    anApplication(adminEmail = adminEmail).withState(InState.pendingGatekeeperApproval("test@test.com", "test name"))

  def aStandardPendingResponsibleIndividualVerificationApplication(adminEmail: LaxEmailAddress = "admin@example.com".toLaxEmail): ApplicationWithCollaborators =
    anApplication(adminEmail = adminEmail).withState(InState.pendingResponsibleIndividualVerification("admin@example.com", "admin name"))

  def standardAccess(
      redirectUris: List[RedirectUri] = List("https://redirect1", "https://redirect2").map(RedirectUri.unsafeApply(_)),
      termsAndConditionsUrl: Option[String] = Some("http://example.com/terms"),
      privacyPolicyUrl: Option[String] = Some("http://example.com/privacy")
    ): Access.Standard = {

    Access.Standard(redirectUris, termsAndConditionsUrl, privacyPolicyUrl)
  }

  def anROPCApplication(): ApplicationWithCollaborators = anApplication(access = ropcAccess())

  def ropcAccess(scopes: Set[String] = Set(randomString(10), randomString(10), randomString(10))): Access = Access.Ropc(scopes)

  def aPrivilegedApplication(): ApplicationWithCollaborators = anApplication(access = privilegedAccess())

  def privilegedAccess(scopes: Set[String] = Set(randomString(10), randomString(10), randomString(10))): Access.Privileged = Access.Privileged(None, scopes)

  def tokens(clientId: ClientId = ClientId(randomString(28)), clientSecret: String = randomString(28), accessToken: String = randomString(28)): ApplicationToken = {
    ApplicationToken(List(aClientSecret()), accessToken)
  }

  private def aClientSecret() = ClientSecretResponse(ClientSecret.Id.random, randomUUID.toString, instant)

  implicit class AppAugment(val app: ApplicationWithCollaborators) {
    final def withName(name: String): ApplicationWithCollaborators = app.modify(_.copy(name = ApplicationName(name)))

    final def withDescription(description: Option[String]): ApplicationWithCollaborators = app.modify(_.copy(description = description))

    final def withTeamMember(email: LaxEmailAddress, userRole: Collaborator.Role): ApplicationWithCollaborators =
      app.copy(collaborators = app.collaborators + Collaborator(email, userRole, UserId.random))

    final def withTeamMembers(teamMembers: Set[Collaborator]): ApplicationWithCollaborators = app.copy(collaborators = teamMembers)

    final def withState(state: ApplicationState): ApplicationWithCollaborators = app.withState(state)

    final def withCheckInformation(checkInformation: CheckInformation): ApplicationWithCollaborators = app.modify(_.copy(checkInformation = Some(checkInformation)))

    def standardAccess: Access.Standard = {
      if (app.access.accessType != AccessType.STANDARD) {
        throw new IllegalArgumentException(s"You can only use this method on a Standard application. Your app was ${app.access.accessType}")
      } else {
        app.access.asInstanceOf[Access.Standard]
      }
    }

    final def withRedirectUris(someRedirectUris: List[RedirectUri]): ApplicationWithCollaborators = app.withAccess(standardAccess.copy(redirectUris = someRedirectUris))

    final def withTermsAndConditionsUrl(url: Option[String]): ApplicationWithCollaborators = app.withAccess(standardAccess.copy(termsAndConditionsUrl = url))

    final def withPrivacyPolicyUrl(url: Option[String]): ApplicationWithCollaborators = app.withAccess(standardAccess.copy(privacyPolicyUrl = url))
  }
}
