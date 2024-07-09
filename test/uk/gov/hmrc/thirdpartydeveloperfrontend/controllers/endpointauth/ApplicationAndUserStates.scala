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

import java.time.{Instant, LocalDate, Period}
import java.util.UUID
import scala.concurrent.Future

import cats.data.NonEmptyList

import play.api.libs.crypto.CookieSigner
import play.api.mvc.Cookie
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.{RegisterAuthAppResponse, RegisterSmsSuccessResponse}
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ResponsibleIndividualVerificationState.INITIAL
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission.Status.Granted
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.Developer
import uk.gov.hmrc.apiplatform.modules.tpd.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailPreferences
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaId
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.MfaDetailBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector.CoreUserDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.SupportSessionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions._

trait HasApplication extends HasAppDeploymentEnvironment with HasUserWithRole with HasAppState with MfaDetailBuilder {
  val applicationId: ApplicationId = ApplicationId.random
  val submissionId: SubmissionId   = SubmissionId.random
  val clientId: ClientId           = ClientId.random
  val applicationName              = "my app"
  val createdOn: Instant           = LocalDate.of(2020, 1, 1).asInstant

  def describeApplication: String
  def access: Access
  def checkInformation: Option[CheckInformation]

  def application: Application                            = Application(
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
    IpAllowlist()
  )
  lazy val redirectUrl                                    = RedirectUri.unsafeApply("https://example.com/redirect-here")
  lazy val apiContext: ApiContext                         = ApiContext("ctx")
  lazy val apiVersion: ApiVersionNbr                      = ApiVersionNbr("1.0")
  lazy val apiIdentifier: ApiIdentifier                   = ApiIdentifier(apiContext, apiVersion)
  lazy val apiFieldName: FieldName                        = FieldName("my_field")
  lazy val apiFieldValue: FieldValue                      = FieldValue("my value")
  lazy val apiPpnsFieldName: FieldName                    = FieldName("my_ppns_field")
  lazy val apiPpnsFieldValue: FieldValue                  = FieldValue("my ppns value")
  lazy val appWithSubsIds: ApplicationWithSubscriptionIds = ApplicationWithSubscriptionIds.from(application).copy(subscriptions = Set(apiIdentifier))
  lazy val privacyPolicyUrl                               = "http://example.com/priv"
  lazy val termsConditionsUrl                             = "http://example.com/tcs"
  lazy val category                                       = "category1"

  lazy val appWithSubsData: ApplicationWithSubscriptionData = ApplicationWithSubscriptionData(
    application,
    Set(apiIdentifier),
    Map(
      apiContext -> Map(ApiVersionNbr("1.0") -> Map(apiFieldName -> apiFieldValue, apiPpnsFieldName -> apiPpnsFieldValue))
    )
  )
  lazy val questionnaireId: Questionnaire.Id                = Questionnaire.Id.random
  lazy val question: Question.AcknowledgementOnly           = Question.AcknowledgementOnly(Question.Id.random, Wording("hi"), None)
  lazy val questionItem: QuestionItem                       = QuestionItem(question)
  lazy val questionnaire: Questionnaire                     = Questionnaire(questionnaireId, Questionnaire.Label("label"), NonEmptyList.one(questionItem))

  lazy val questionIdsOfInterest: QuestionIdsOfInterest                  = QuestionIdsOfInterest(
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
  lazy val groupOfQuestionnaires: GroupOfQuestionnaires                  = GroupOfQuestionnaires("heading", NonEmptyList.one(questionnaire))
  lazy val answersToQuestions: Map[Question.Id, ActualAnswer.TextAnswer] = Map(question.id -> ActualAnswer.TextAnswer("yes"))
  lazy val submissionInstance: Submission.Instance                       = Submission.Instance(submissionIndex, answersToQuestions, NonEmptyList.one(Granted(instant, "mr jones", None, None)))

  lazy val submission: Submission                       = Submission(
    SubmissionId.random,
    applicationId,
    instant,
    NonEmptyList.one(groupOfQuestionnaires),
    questionIdsOfInterest,
    NonEmptyList.one(submissionInstance),
    Map.empty
  )
  lazy val questionnaireProgress: QuestionnaireProgress = QuestionnaireProgress(QuestionnaireState.Completed, List(question.id))

  lazy val extendedSubmission: ExtendedSubmission = ExtendedSubmission(
    submission,
    Map(
      questionnaireId -> questionnaireProgress
    )
  )

  lazy val subscriptionFieldDefinitions: Map[FieldName, SubscriptionFieldDefinition] = Map(
    apiFieldName     -> SubscriptionFieldDefinition(apiFieldName, "field desc", "field short desc", "hint", "STRING", AccessRequirements.Default),
    apiPpnsFieldName -> SubscriptionFieldDefinition(apiPpnsFieldName, "field desc", "field short desc", "hint", "PPNSField", AccessRequirements.Default)
  )

  lazy val defaultApiVersion: ApiVersion = ApiVersion(ApiVersionNbr("1.0"), ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty)

  lazy val defaultApiDefinition: ApiDefinition = ApiDefinition(
    serviceName = ServiceName("service name"),
    serviceBaseUrl = "http://serviceBaseURL",
    name = "api name",
    description = "api description",
    context = apiContext,
    versions = Map(apiVersion -> defaultApiVersion),
    isTestSupport = false,
    categories = List.empty
  )

  lazy val allPossibleSubscriptions: List[ApiDefinition]                            = List(defaultApiDefinition)
  lazy val responsibleIndividualVerificationId: ResponsibleIndividualVerificationId = ResponsibleIndividualVerificationId(UUID.randomUUID().toString)

  lazy val submissionIndex                              = 1
  lazy val responsibleIndividual: ResponsibleIndividual = ResponsibleIndividual(FullName("mr responsible"), "ri@example.com".toLaxEmail)

  lazy val responsibleIndividualVerification: ResponsibleIndividualUpdateVerification = ResponsibleIndividualUpdateVerification(
    responsibleIndividualVerificationId,
    applicationId,
    SubmissionId.random,
    submissionIndex,
    applicationName,
    createdOn,
    responsibleIndividual,
    "admin@example.com",
    "Mr Admin".toLaxEmail,
    INITIAL
  )

  lazy val responsibleIndividualVerificationWithDetails: ResponsibleIndividualVerificationWithDetails = ResponsibleIndividualVerificationWithDetails(
    responsibleIndividualVerification,
    responsibleIndividual,
    "mr submitter",
    "submitter@example.com".toLaxEmail
  )
  lazy val authAppMfaId: MfaId                                                                        = verifiedAuthenticatorAppMfaDetail.id
  lazy val smsMfaId: MfaId                                                                            = verifiedSmsMfaDetail.id
  lazy val registerAuthAppResponse: RegisterAuthAppResponse                                           = RegisterAuthAppResponse(authAppMfaId, "secret")
  lazy val registerSmsResponse: RegisterSmsSuccessResponse                                            = RegisterSmsSuccessResponse(smsMfaId, verifiedSmsMfaDetail.mobileNumber)
}

trait IsOldJourneyStandardApplication extends HasApplication {
  def describeApplication = "an Old Journey application with Standard access"
  def access: Access      = Access.Standard(List(redirectUrl), None, None, Set.empty, None, None)

  def checkInformation: Option[CheckInformation] = Some(CheckInformation(
    contactDetails = Some(ContactDetails(FullName(s"$userFirstName $userLastName"), userEmail, "01611234567")),
    confirmedName = true,
    apiSubscriptionConfigurationsConfirmed = true,
    providedPrivacyPolicyURL = true,
    providedTermsAndConditionsURL = true,
    applicationDetails = None,
    teamConfirmed = true,
    termsOfUseAgreements = List(TermsOfUseAgreement(userEmail, instant, "1.0"))
  ))
}

trait IsNewJourneyStandardApplication extends HasApplication {
  def describeApplication = "a New Journey application with Standard access"

  def access: Access                             = Access.Standard(
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
  def checkInformation: Option[CheckInformation] = None
}

trait IsNewJourneyStandardApplicationWithoutSubmission extends HasApplication {
  def describeApplication                        = "a New Journey application with Standard access but no submission"
  def access: Access                             = Access.Standard(List(redirectUrl), None, None, Set.empty, None, None)
  def checkInformation: Option[CheckInformation] = None
}

trait HasUserWithRole extends MockConnectors with MfaDetailBuilder {
  lazy val userEmail: LaxEmailAddress = "user@example.com".toLaxEmail
  lazy val userId: UserId             = UserId.random
  lazy val userFirstName              = "Bob"
  lazy val userLastName               = "Example"
  lazy val userFullName               = s"$userFirstName $userLastName"
  lazy val userPhone                  = "01611234567"
  lazy val userPassword               = "S3curE-Pa$$w0rd!"
  lazy val organisation               = "Big Corp"

  def describeUserRole: String

  def developer: Developer = Developer(
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
  def describeUserRole                        = "The user is an Admin on the application team"
  def maybeCollaborator: Option[Collaborator] = Some(Collaborator(userEmail, Collaborator.Roles.ADMINISTRATOR, userId))
}

trait UserIsDeveloper extends UserIsTeamMember {
  def describeUserRole                        = "The user is a Developer on the application team"
  def maybeCollaborator: Option[Collaborator] = Some(Collaborator(userEmail, Collaborator.Roles.DEVELOPER, userId))
}

trait UserIsNotOnApplicationTeam extends HasUserWithRole with HasApplication {
  val otherApp: Application                               = application.copy(id = ApplicationId.random, collaborators = Set(Collaborator(userEmail, Collaborator.Roles.DEVELOPER, userId)))
  val otherAppWithSubsIds: ApplicationWithSubscriptionIds = ApplicationWithSubscriptionIds.from(otherApp).copy(subscriptions = Set(apiIdentifier))
  when(tpaProductionConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(otherAppWithSubsIds)))
  when(tpaSandboxConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(otherAppWithSubsIds)))
  def describeUserRole                                    = "The user is not a member of the application team"
  def maybeCollaborator: Option[Collaborator]             = None
}

trait HasUserSession extends HasUserWithRole with UpdatesRequest {
  lazy val sessionId        = UserSessionId.random
  lazy val supportSessionId = SupportSessionId.random
  def describeAuthenticationState: String
  def loggedInState: LoggedInState
  def session: Session      = Session(sessionId, developer, loggedInState)
  implicit val cookieSigner: CookieSigner

  override def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = {
    request.withCookies(
      Cookie("SUPPORT_SESS_ID", cookieSigner.sign(sessionId.toString) + sessionId.toString, None, "path", None, false, false)
    )
  }
}

trait UserIsAuthenticated extends HasUserSession with UpdatesRequest {
  def describeAuthenticationState  = "and is authenticated"
  def loggedInState: LoggedInState = LoggedInState.LOGGED_IN

  when(tpdConnector.register(*)(*)).thenReturn(Future.successful(EmailAlreadyInUse))
  when(tpdConnector.findUserId(*[LaxEmailAddress])(*)).thenReturn(Future.successful(Some(CoreUserDetails(userEmail, userId))))

  implicit val cookieSigner: CookieSigner

  override def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = {
    request.withCookies(
      Cookie("PLAY2AUTH_SESS_ID", cookieSigner.sign(sessionId.toString) + sessionId.toString, None, "path", None, false, false),
      Cookie("SUPPORT_SESS_ID", cookieSigner.sign(supportSessionId.toString) + supportSessionId.toString, None, "path", None, false, false)
    ).withSession(
      ("email", userEmail.text),
      ("emailAddress", userEmail.text),
      ("nonce", "123"),
      ("userId", developer.userId.value.toString)
    )
  }
}

trait UserIsNotAuthenticated extends HasUserSession {
  def describeAuthenticationState  = "and is not authenticated"
  def loggedInState: LoggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA

  when(tpdConnector.register(*)(*)).thenReturn(Future.successful(RegistrationSuccessful))
  when(tpdConnector.findUserId(*[LaxEmailAddress])(*)).thenReturn(Future.successful(None))
}

trait HasAppDeploymentEnvironment {
  def describeDeployment = s"deployed to $environment"
  def environment: Environment
}

trait AppDeployedToProductionEnvironment extends HasAppDeploymentEnvironment {
  def environment: Environment = Environment.PRODUCTION
}

trait AppDeployedToSandboxEnvironment extends HasAppDeploymentEnvironment {
  def environment: Environment = Environment.SANDBOX
}

trait HasAppState extends FixedClock {
  def describeAppState = s"in state ${state.name}"
  def state: ApplicationState
}

trait AppHasProductionStatus extends HasAppState {
  def state: ApplicationState = ApplicationState(State.PRODUCTION, Some("requester@example.com"), Some("mr requester"), Some("code123"), instant)
}

trait AppHasPendingGatekeeperApprovalStatus extends HasAppState {
  def state: ApplicationState = ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some("requester@example.com"), Some("mr requester"), None, instant)
}

trait AppHasTestingStatus extends HasAppState {
  def state: ApplicationState = ApplicationState(updatedOn = instant)
}

trait UpdatesRequest {
  def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T]
}
