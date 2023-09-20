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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

import java.time.{LocalDateTime, Period}
import java.util.UUID
import scala.concurrent.Future

import cats.data.NonEmptyList

import play.api.libs.crypto.CookieSigner
import play.api.mvc.Cookie
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiStatus, ApiAccess, ApiCategory}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.{RegisterAuthAppResponse, RegisterSmsSuccessResponse}
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ResponsibleIndividualVerificationState.INITIAL
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission.Status.Granted
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.MfaDetailBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector.CoreUserDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions._

trait HasApplication extends HasAppDeploymentEnvironment with HasUserWithRole with HasAppState with MfaDetailBuilder {
  val applicationId   = ApplicationId.random
  val clientId        = ClientId.random
  val applicationName = "my app"
  val createdOn       = LocalDateTime.of(2020, 1, 1, 0, 0, 0)

  def describeApplication: String
  def access: Access
  def checkInformation: Option[CheckInformation]

  def application             = Application(
    applicationId,
    clientId,
    applicationName,
    createdOn,
    None,
    None,
    Period.ofYears(1),
    environment,
    None,
    maybeCollaborator match {
      case Some(collaborator) => Set(collaborator)
      case None               => Set()
    },
    access,
    state,
    checkInformation,
    IpAllowlist(false, Set.empty)
  )
  lazy val redirectUrl        = "https://example.com/redirect-here"
  lazy val apiContext         = ApiContext("ctx")
  lazy val apiVersion         = ApiVersionNbr("1.0")
  lazy val apiIdentifier      = ApiIdentifier(apiContext, apiVersion)
  lazy val apiFieldName       = FieldName("my_field")
  lazy val apiFieldValue      = FieldValue("my value")
  lazy val apiPpnsFieldName   = FieldName("my_ppns_field")
  lazy val apiPpnsFieldValue  = FieldValue("my ppns value")
  lazy val appWithSubsIds     = ApplicationWithSubscriptionIds.from(application).copy(subscriptions = Set(apiIdentifier))
  lazy val privacyPolicyUrl   = "http://example.com/priv"
  lazy val termsConditionsUrl = "http://example.com/tcs"
  lazy val category           = "category1"

  lazy val appWithSubsData = ApplicationWithSubscriptionData(
    application,
    Set(apiIdentifier),
    Map(
      apiContext -> Map(ApiVersionNbr("1.0") -> Map(apiFieldName -> apiFieldValue, apiPpnsFieldName -> apiPpnsFieldValue))
    )
  )
  lazy val questionnaireId = Questionnaire.Id.random
  lazy val question        = AcknowledgementOnly(Question.Id.random, Wording("hi"), None)
  lazy val questionItem    = QuestionItem(question)
  lazy val questionnaire   = Questionnaire(questionnaireId, Questionnaire.Label("label"), NonEmptyList.one(questionItem))

  lazy val questionIdsOfInterest = QuestionIdsOfInterest(
    Question.Id.random,
    Question.Id.random,
    Question.Id.random,
    Question.Id.random,
    Question.Id.random,
    Question.Id.random,
    Question.Id.random,
    Question.Id.random,
    Question.Id.random,
    Question.Id.random,
    Question.Id.random
  )
  lazy val groupOfQuestionnaires = GroupOfQuestionnaires("heading", NonEmptyList.one(questionnaire))
  lazy val answersToQuestions    = Map(question.id -> TextAnswer("yes"))
  lazy val submissionInstance    = Submission.Instance(submissionIndex, answersToQuestions, NonEmptyList.one(Granted(LocalDateTime.now, "mr jones", None, None)))

  lazy val submission            = Submission(
    submissionId,
    applicationId,
    LocalDateTime.now,
    NonEmptyList.one(groupOfQuestionnaires),
    questionIdsOfInterest,
    NonEmptyList.one(submissionInstance),
    Map.empty
  )
  lazy val questionnaireProgress = QuestionnaireProgress(QuestionnaireState.Completed, List(question.id))

  lazy val extendedSubmission = ExtendedSubmission(
    submission,
    Map(
      questionnaireId -> questionnaireProgress
    )
  )

  lazy val subscriptionFieldDefinitions = Map(
    apiFieldName     -> SubscriptionFieldDefinition(apiFieldName, "field desc", "field short desc", "hint", "STRING", AccessRequirements.Default),
    apiPpnsFieldName -> SubscriptionFieldDefinition(apiPpnsFieldName, "field desc", "field short desc", "hint", "PPNSField", AccessRequirements.Default)
  )

  lazy val allPossibleSubscriptions            = Map(
    apiContext -> ApiData("service name", "api name", false, Map(apiVersion -> VersionData(ApiStatus.STABLE, ApiAccess.PUBLIC)), List(ApiCategory.OTHER))

  )
  lazy val responsibleIndividualVerificationId = ResponsibleIndividualVerificationId(UUID.randomUUID().toString)
  lazy val submissionId                        = Submission.Id.random
  lazy val submissionIndex                     = 1
  lazy val responsibleIndividual               = ResponsibleIndividual.build("mr responsible", "ri@example.com".toLaxEmail)

  lazy val responsibleIndividualVerification = ResponsibleIndividualUpdateVerification(
    responsibleIndividualVerificationId,
    applicationId,
    submissionId,
    submissionIndex,
    applicationName,
    createdOn,
    responsibleIndividual,
    "admin@example.com",
    "Mr Admin".toLaxEmail,
    INITIAL
  )

  lazy val responsibleIndividualVerificationWithDetails = ResponsibleIndividualVerificationWithDetails(
    responsibleIndividualVerification,
    responsibleIndividual,
    "mr submitter",
    "submitter@example.com".toLaxEmail
  )
  lazy val authAppMfaId                                 = verifiedAuthenticatorAppMfaDetail.id
  lazy val smsMfaId                                     = verifiedSmsMfaDetail.id
  lazy val registerAuthAppResponse                      = RegisterAuthAppResponse(authAppMfaId, "secret")
  lazy val registerSmsResponse                          = RegisterSmsSuccessResponse(smsMfaId, verifiedSmsMfaDetail.mobileNumber)
}

trait IsOldJourneyStandardApplication extends HasApplication {
  def describeApplication = "an Old Journey application with Standard access"
  def access: Access      = Standard(List(redirectUrl), None, None, Set.empty, None, None)

  def checkInformation = Some(CheckInformation(
    true,
    true,
    true,
    Some(ContactDetails(s"$userFirstName $userLastName", userEmail, "01611234567")),
    true,
    true,
    true,
    List(TermsOfUseAgreement(userEmail, LocalDateTime.now(), "1.0"))
  ))
}

trait IsNewJourneyStandardApplication extends HasApplication {
  def describeApplication = "a New Journey application with Standard access"

  def access: Access   = Standard(
    List(redirectUrl),
    None,
    None,
    Set.empty,
    None,
    Some(ImportantSubmissionData(
      None,
      responsibleIndividual,
      Set.empty,
      TermsAndConditionsLocations.Url(termsConditionsUrl),
      PrivacyPolicyLocations.Url(privacyPolicyUrl),
      List.empty
    ))
  )
  def checkInformation = None
}

trait IsNewJourneyStandardApplicationWithoutSubmission extends HasApplication {
  def describeApplication = "a New Journey application with Standard access but no submission"
  def access: Access      = Standard(List(redirectUrl), None, None, Set.empty, None, None)
  def checkInformation    = None
}

trait HasUserWithRole extends MockConnectors with MfaDetailBuilder {
  lazy val userEmail: LaxEmailAddress = "user@example.com".toLaxEmail
  lazy val userId                     = UserId.random
  lazy val userFirstName              = "Bob"
  lazy val userLastName               = "Example"
  lazy val userFullName               = s"$userFirstName $userLastName"
  lazy val userPhone                  = "01611234567"
  lazy val userPassword               = "S3curE-Pa$$w0rd!"
  lazy val organisation               = "Big Corp"

  def describeUserRole: String

  def developer = Developer(
    userId,
    userEmail,
    userFirstName,
    userLastName,
    None,
    List(verifiedAuthenticatorAppMfaDetail),
    EmailPreferences.noPreferences
  )
  def maybeCollaborator: Option[Collaborator]
}

trait UserIsTeamMember extends HasUserWithRole with HasApplication {
  when(tpaProductionConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
  when(tpaSandboxConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
}

trait UserIsAdmin extends UserIsTeamMember {
  def describeUserRole  = "The user is an Admin on the application team"
  def maybeCollaborator = Some(Collaborator(userEmail, Collaborator.Roles.ADMINISTRATOR, userId))
}

trait UserIsDeveloper extends UserIsTeamMember {
  def describeUserRole  = "The user is a Developer on the application team"
  def maybeCollaborator = Some(Collaborator(userEmail, Collaborator.Roles.DEVELOPER, userId))
}

trait UserIsNotOnApplicationTeam extends HasUserWithRole with HasApplication {
  val otherApp            = application.copy(id = ApplicationId.random, collaborators = Set(Collaborator(userEmail, Collaborator.Roles.DEVELOPER, userId)))
  val otherAppWithSubsIds = ApplicationWithSubscriptionIds.from(otherApp).copy(subscriptions = Set(apiIdentifier))
  when(tpaProductionConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(otherAppWithSubsIds)))
  when(tpaSandboxConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(otherAppWithSubsIds)))
  def describeUserRole    = "The user is not a member of the application team"
  def maybeCollaborator   = None
}

trait HasUserSession extends HasUserWithRole {
  lazy val sessionId = "my session"
  def describeAuthenticationState: String
  def loggedInState: LoggedInState
  def session        = Session(sessionId, developer, loggedInState)
}

trait UserIsAuthenticated extends HasUserSession with UpdatesRequest {
  def describeAuthenticationState = "and is authenticated"
  def loggedInState               = LoggedInState.LOGGED_IN

  when(tpdConnector.register(*)(*)).thenReturn(Future.successful(EmailAlreadyInUse))
  when(tpdConnector.findUserId(*[LaxEmailAddress])(*)).thenReturn(Future.successful(Some(CoreUserDetails(userEmail, userId))))

  implicit val cookieSigner: CookieSigner

  override def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = {
    request.withCookies(
      Cookie("PLAY2AUTH_SESS_ID", cookieSigner.sign(sessionId) + sessionId, None, "path", None, false, false)
    ).withSession(
      ("email", userEmail.text),
      ("emailAddress", userEmail.text),
      ("nonce", "123"),
      ("userId", developer.userId.value.toString)
    )
  }
}

trait UserIsNotAuthenticated extends HasUserSession {
  def describeAuthenticationState = "and is not authenticated"
  def loggedInState               = LoggedInState.PART_LOGGED_IN_ENABLING_MFA

  when(tpdConnector.register(*)(*)).thenReturn(Future.successful(RegistrationSuccessful))
  when(tpdConnector.findUserId(*[LaxEmailAddress])(*)).thenReturn(Future.successful(None))
}

trait HasAppDeploymentEnvironment {
  def describeDeployment = s"deployed to $environment"
  def environment: Environment
}

trait AppDeployedToProductionEnvironment extends HasAppDeploymentEnvironment {
  def environment = Environment.PRODUCTION
}

trait AppDeployedToSandboxEnvironment extends HasAppDeploymentEnvironment {
  def environment = Environment.SANDBOX
}

trait HasAppState {
  def describeAppState = s"in state ${state.name}"
  def state: ApplicationState
}

trait AppHasProductionStatus extends HasAppState {
  def state = ApplicationState.production("requester@example.com", "mr requester", "code123")
}

trait AppHasPendingGatekeeperApprovalStatus extends HasAppState {
  def state = ApplicationState.pendingGatekeeperApproval("requester@example.com", "mr requester")
}

trait AppHasTestingStatus extends HasAppState {
  def state = ApplicationState.testing
}

trait UpdatesRequest {
  def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T]
}
