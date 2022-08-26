/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ResponsibleIndividualVerification, ResponsibleIndividualVerificationId, ResponsibleIndividualVerificationWithDetails, Submission}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector.CoreUserDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APIAccessType.PUBLIC
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APIStatus.STABLE
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{APIAccess, ApiContext, ApiIdentifier, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment.{PRODUCTION, SANDBOX}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions._

import java.time.{LocalDateTime, Period}
import java.util.UUID
import scala.concurrent.Future


trait HasApplication extends HasAppDeploymentEnvironment with HasUserWithRole with HasAppState {
  val applicationId = ApplicationId.random
  val clientId = ClientId.random
  val applicationName = "my app"
  val createdOn = LocalDateTime.of(2020, 1, 1, 0, 0, 0)

  def describeApplication: String
  def access: Access
  def checkInformation: Option[CheckInformation]
  def application = Application(
    applicationId, clientId, applicationName, createdOn, None, None, Period.ofYears(1), environment, None,
    maybeCollaborator match {
      case Some(collaborator) => Set(collaborator)
      case None => Set()
    },
    access, state, checkInformation, IpAllowlist(false, Set.empty)
  )
  lazy val redirectUrl = "https://example.com/redirect-here"
  lazy val apiContext = ApiContext("ctx")
  lazy val apiVersion = ApiVersion("1.0")
  lazy val apiIdentifier = ApiIdentifier(apiContext, apiVersion)
  lazy val apiFieldName = FieldName("my_field")
  lazy val apiFieldValue = FieldValue("my value")
  lazy val apiPpnsFieldName = FieldName("my_ppns_field")
  lazy val apiPpnsFieldValue = FieldValue("my ppns value")
  lazy val appWithSubsIds = ApplicationWithSubscriptionIds.from(application).copy(subscriptions = Set(apiIdentifier))
  lazy val privacyPolicyUrl = "http://example.com/priv"
  lazy val termsConditionsUrl = "http://example.com/tcs"
  lazy val appWithSubsData = ApplicationWithSubscriptionData(application, Set(apiIdentifier), Map(
    apiContext -> Map(ApiVersion("1.0") -> Map(apiFieldName -> apiFieldValue, apiPpnsFieldName -> apiPpnsFieldValue))
  ))
  lazy val subscriptionFieldDefinitions = Map(
    apiFieldName -> SubscriptionFieldDefinition(apiFieldName, "field desc", "field short desc", "hint", "STRING", AccessRequirements.Default),
    apiPpnsFieldName -> SubscriptionFieldDefinition(apiPpnsFieldName, "field desc", "field short desc", "hint", "PPNSField", AccessRequirements.Default)
  )
  lazy val allPossibleSubscriptions = Map(
    apiContext -> ApiData("service name", "api name", false, Map(apiVersion -> VersionData(STABLE, APIAccess(PUBLIC))), List(ApiCategory("category")))
  )
  lazy val responsibleIndividualVerificationId = ResponsibleIndividualVerificationId(UUID.randomUUID().toString)
  lazy val submissionId = Submission.Id.random
  lazy val submissionIndex = 1
  lazy val responsibleIndividual = ResponsibleIndividual.build("mr responsible", "ri@example.com")
  lazy val responsibleIndividualVerification = ResponsibleIndividualVerification(responsibleIndividualVerificationId, applicationId, submissionId, submissionIndex, applicationName, createdOn)
  lazy val responsibleIndividualVerificationWithDetails = ResponsibleIndividualVerificationWithDetails(
    responsibleIndividualVerification, responsibleIndividual, "mr submitter", "submitter@example.com"
  )
  lazy val mfaId = MfaId.random

}
trait IsOldJourneyStandardApplication extends HasApplication {
  def describeApplication = "Old Journey application with Standard access"
  def access: Access = Standard(List(redirectUrl), None, None, Set.empty, None, None)
  def checkInformation = Some(CheckInformation(true, true, true, Some(ContactDetails(s"$userFirstName $userLastName", userEmail, "01611234567")), true, true, true,
    List(TermsOfUseAgreement(userEmail, LocalDateTime.now(), "1.0"))))
}
trait IsNewJourneyStandardApplication extends HasApplication {
  def describeApplication = "New Journey application with Standard access"
  def access: Access = Standard(List(redirectUrl), None, None, Set.empty, None, Some(ImportantSubmissionData(
    None, responsibleIndividual, Set.empty, TermsAndConditionsLocation.Url(termsConditionsUrl), PrivacyPolicyLocation.Url(privacyPolicyUrl), List.empty
  )))
  def checkInformation = None
}

trait HasUserWithRole extends MockConnectors {
  lazy val userEmail = "user@example.com"
  lazy val userId = UserId.random
  lazy val userFirstName = "Bob"
  lazy val userLastName = "Example"
  lazy val userFullName = s"$userFirstName $userLastName"
  lazy val userPhone = "01611234567"
  lazy val userPassword = "S3curE-Pa$$w0rd!"
  lazy val organisation = "Big Corp"

  def describeUserRole: String
  def developer = Developer(
    userId, userEmail, userFirstName, userLastName, None, List.empty, EmailPreferences.noPreferences
  )
  def maybeCollaborator: Option[Collaborator]

}
trait UserIsTeamMember extends HasUserWithRole with HasApplication {
  when(tpaProductionConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
  when(tpaSandboxConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
}
trait UserIsAdmin extends UserIsTeamMember {
  def describeUserRole = "User is an Admin"
  def maybeCollaborator = Some(Collaborator(userEmail, CollaboratorRole.ADMINISTRATOR, userId))
}
trait UserIsDeveloper extends UserIsTeamMember {
  def describeUserRole = "User is an Developer"
  def maybeCollaborator = Some(Collaborator(userEmail, CollaboratorRole.DEVELOPER, userId))
}
trait UserIsNotOnApplicationTeam extends HasUserWithRole {
  def describeUserRole = "User is not a member of the application team"
  def maybeCollaborator = None
}

trait HasUserSession extends HasUserWithRole {
  lazy val sessionId = "my session"
  def describeAuthenticationState: String
  def loggedInState: LoggedInState
  def session = Session(sessionId, developer, loggedInState)
}
trait UserIsAuthenticated extends HasUserSession {
  def describeAuthenticationState = "User is authenticated"
  def loggedInState = LoggedInState.LOGGED_IN

  when(tpdConnector.register(*)(*)).thenReturn(Future.successful(EmailAlreadyInUse))
  when(tpdConnector.findUserId(*)(*)).thenReturn(Future.successful(Some(CoreUserDetails(userEmail, userId))))
}
trait UserIsNotAuthenticated extends HasUserSession {
  def describeAuthenticationState = "User is not authenticated"
  def loggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA

  when(tpdConnector.register(*)(*)).thenReturn(Future.successful(RegistrationSuccessful))
  when(tpdConnector.findUserId(*)(*)).thenReturn(Future.successful(None))
}


trait HasAppDeploymentEnvironment {
  def describeDeployment = s"App is deployed to $environment"
  def environment: Environment
}
trait AppDeployedToProductionEnvironment extends HasAppDeploymentEnvironment {
  def environment = PRODUCTION
}
trait AppDeployedToSandboxEnvironment extends HasAppDeploymentEnvironment {
  def environment = SANDBOX
}

trait HasAppState {
  def describeAppState = s"App has state ${state.name}"
  def state: ApplicationState
}
trait AppHasProductionStatus extends HasAppState {
  def state = ApplicationState.production("requester@example.com", "code123")
}
trait AppHasTestingStatus extends HasAppState {
  def state = ApplicationState.testing
}

