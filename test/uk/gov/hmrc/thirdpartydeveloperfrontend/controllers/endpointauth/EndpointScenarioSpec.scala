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

import java.time.{Instant, LocalDateTime}
import scala.concurrent.Future
import scala.io.Source

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Mode
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.test.Helpers.{redirectLocation, route, status}
import play.api.test.{CSRFTokenHelper, FakeRequest, Writeables}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiContext, ApiIdentifier, ApiVersion}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.developers.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.dynamics.connectors.ThirdPartyDeveloperDynamicsConnector
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.models.{MfaAction, MfaId, MfaType}
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{Question, ResponsibleIndividualVerificationId, Submission}
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.{ApiSubscriptions, GetProductionCredentialsFlow}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationNameValidationJson.ApplicationNameValidationResult
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{UpdateProfileRequest, User}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.{APICategoryDisplayDetails, EmailPreferences}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SaveSubscriptionFieldsSuccessResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.Fields
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.{ApplicationUpdateSuccessful, ApplicationUpliftSuccessful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.ExcludeFromCoverage
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

object EndpointScenarioSpec {

  def parseEndpoint(text: String, pathPrefix: String): Option[Endpoint] = {
    text.trim.split("\\s+", 3) match {
      case Array(verb, path, method) if verb.matches("[A-Z]+") => Some(Endpoint(verb, pathPrefix + path, method))
      case _                                                   => None
    }
  }
}

abstract class EndpointScenarioSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Writeables
    with MockConnectors
    with HasApplication
    with HasUserWithRole
    with HasUserSession
    with UpdatesRequest {
  import EndpointScenarioSpec._

  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  override def fakeApplication() = {
    GuiceApplicationBuilder()
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .overrides(bind[ThirdPartyDeveloperConnector].toInstance(tpdConnector))
      .overrides(bind[ThirdPartyApplicationProductionConnector].toInstance(tpaProductionConnector))
      .overrides(bind[ThirdPartyApplicationSandboxConnector].toInstance(tpaSandboxConnector))
      .overrides(bind[DeskproConnector].toInstance(deskproConnector))
      .overrides(bind[FlowRepository].toInstance(flowRepository))
      .overrides(bind[ApmConnector].toInstance(apmConnector))
      .overrides(bind[SandboxSubscriptionFieldsConnector].toInstance(sandboxSubsFieldsConnector))
      .overrides(bind[ProductionSubscriptionFieldsConnector].toInstance(productionSubsFieldsConnector))
      .overrides(bind[SandboxPushPullNotificationsConnector].toInstance(sandboxPushPullNotificationsConnector))
      .overrides(bind[ProductionPushPullNotificationsConnector].toInstance(productionPushPullNotificationsConnector))
      .overrides(bind[ThirdPartyApplicationSubmissionsConnector].toInstance(thirdPartyApplicationSubmissionsConnector))
      .overrides(bind[ThirdPartyDeveloperMfaConnector].toInstance(thirdPartyDeveloperMfaConnector))
      .overrides(bind[ThirdPartyDeveloperDynamicsConnector].toInstance(thirdPartyDeveloperDynamicsConnector))
      .in(Mode.Test)
      .build()
  }

  when(apmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(Future.successful(Some(appWithSubsData)))
  when(apmConnector.getAllFieldDefinitions(*[Environment])(*)).thenReturn(Future.successful(Map(apiContext -> Map(apiVersion -> subscriptionFieldDefinitions))))
  when(apmConnector.fetchAllOpenAccessApis(*[Environment])(*)).thenReturn(Future.successful(Map.empty))
  when(apmConnector.fetchAllPossibleSubscriptions(*[ApplicationId])(*)).thenReturn(Future.successful(allPossibleSubscriptions))
  when(apmConnector.fetchCombinedApi(*[String])(*)).thenReturn(Future.successful(Right(CombinedApi("my service", "my service display name", List.empty, REST_API))))
  when(tpaSandboxConnector.fetchCredentials(*[ApplicationId])(*)).thenReturn(Future.successful(ApplicationToken(
    List(ClientSecret("s1id", "s1name", LocalDateTime.now(), None)),
    "secret"
  )))
  when(tpaProductionConnector.fetchCredentials(*[ApplicationId])(*)).thenReturn(Future.successful(ApplicationToken(
    List(ClientSecret("s1id", "s1name", LocalDateTime.now(), None)),
    "secret"
  )))
  when(sandboxPushPullNotificationsConnector.fetchPushSecrets(*[ClientId])(*)).thenReturn(Future.successful(List("secret1")))
  when(productionPushPullNotificationsConnector.fetchPushSecrets(*[ClientId])(*)).thenReturn(Future.successful(List("secret1")))
  when(tpdConnector.fetchByEmails(*[Set[LaxEmailAddress]])(*)).thenReturn(Future.successful(List(User(userEmail, Some(true)))))
  when(tpaSandboxConnector.removeTeamMember(*[ApplicationId], *[LaxEmailAddress], *[LaxEmailAddress], *[Set[LaxEmailAddress]])(*)).thenReturn(Future.successful(
    ApplicationUpdateSuccessful
  ))
  when(tpaProductionConnector.validateName(*[String], *[Option[ApplicationId]])(*)).thenReturn(Future.successful(Valid))
  when(tpaProductionConnector.applicationUpdate(*[ApplicationId], *[ApplicationUpdate])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.applicationUpdate(*[ApplicationId], *[ApplicationUpdate])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.updateApproval(*[ApplicationId], *[CheckInformation])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.updateApproval(*[ApplicationId], *[CheckInformation])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.update(*[ApplicationId], *[UpdateApplicationRequest])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.update(*[ApplicationId], *[UpdateApplicationRequest])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.updateIpAllowlist(*[ApplicationId], *[Boolean], *[Set[String]])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.updateIpAllowlist(*[ApplicationId], *[Boolean], *[Set[String]])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.addClientSecrets(*[ApplicationId], *[ClientSecretRequest])(*)).thenReturn(Future.successful(("1", "2")))
  when(tpaProductionConnector.addClientSecrets(*[ApplicationId], *[ClientSecretRequest])(*)).thenReturn(Future.successful(("1", "2")))

  when(tpaProductionConnector.fetchTermsOfUseInvitations()(*)).thenReturn(Future.successful(List.empty))
  when(tpaProductionConnector.fetchTermsOfUseInvitation(*[ApplicationId])(*)).thenReturn(Future.successful(Some(TermsOfUseInvitation(
    ApplicationId.random,
    Instant.now,
    Instant.now,
    Instant.now
  ))))

  when(apmConnector.addTeamMember(*[ApplicationId], *[AddTeamMemberRequest])(*)).thenReturn(Future.successful(()))
  when(apmConnector.subscribeToApi(*[ApplicationId], *[ApiIdentifier])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(productionSubsFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *[Fields.Alias])(*)).thenReturn(Future.successful(
    SaveSubscriptionFieldsSuccessResponse
  ))
  when(sandboxSubsFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *[Fields.Alias])(*)).thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))
  when(tpaSandboxConnector.validateName(*[String], *[Option[ApplicationId]])(*)).thenReturn(Future.successful(ApplicationNameValidation(ApplicationNameValidationResult(None))))
  when(apmConnector.upliftApplicationV2(*[ApplicationId], *[UpliftData])(*)).thenAnswer((appId: ApplicationId, _: UpliftData) => Future.successful(appId))
  when(apmConnector.fetchUpliftableApiIdentifiers(*)).thenReturn(Future.successful(Set(apiIdentifier)))
  when(apmConnector.fetchAllApis(*)(*)).thenReturn(Future.successful(Map.empty))
  when(apmConnector.fetchUpliftableSubscriptions(*[ApplicationId])(*)).thenReturn(Future.successful(Set(ApiIdentifier(apiContext, apiVersion))))
  when(tpaProductionConnector.requestUplift(*[ApplicationId], *[UpliftRequest])(*)).thenReturn(Future.successful(ApplicationUpliftSuccessful))
  when(deskproConnector.createTicket(*[UserId], *)(*)).thenReturn(Future.successful(TicketCreated))
  when(deskproConnector.createTicket(*[ResponsibleIndividualVerificationId], *)(*)).thenReturn(Future.successful(TicketCreated))
  when(flowRepository.updateLastUpdated(*)).thenReturn(Future.successful(()))

  import scala.reflect.runtime.universe._
  import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows._

  def mockFetchBySessionIdAndFlowType[A <: Flow](a: A)(implicit tt: TypeTag[A]) = {
    when(flowRepository.fetchBySessionIdAndFlowType[A](*[String])(eqTo(tt), *)).thenReturn(
      Future.successful(Some(a))
    )
  }
  mockFetchBySessionIdAndFlowType[GetProductionCredentialsFlow](
    GetProductionCredentialsFlow(sessionId, Some(SellResellOrDistribute("sell")), Some(ApiSubscriptions(Map(ApiIdentifier(apiContext, apiVersion) -> true))))
  )
  mockFetchBySessionIdAndFlowType[IpAllowlistFlow](
    IpAllowlistFlow(sessionId, Set("1.2.3.4"))
  )
  mockFetchBySessionIdAndFlowType[NewApplicationEmailPreferencesFlowV2](
    NewApplicationEmailPreferencesFlowV2(sessionId, EmailPreferences.noPreferences, applicationId, Set.empty, Set.empty, Set.empty)
  )
  mockFetchBySessionIdAndFlowType[EmailPreferencesFlowV2](
    EmailPreferencesFlowV2(sessionId, Set.empty, Map(), Set.empty, List.empty)
  )
  when(flowRepository.deleteBySessionIdAndFlowType(*, *)).thenReturn(Future.successful(true))
  when(flowRepository.saveFlow[GetProductionCredentialsFlow](isA[GetProductionCredentialsFlow])).thenReturn(Future.successful(GetProductionCredentialsFlow(sessionId, None, None)))
  when(flowRepository.saveFlow[IpAllowlistFlow](isA[IpAllowlistFlow])).thenReturn(Future.successful(IpAllowlistFlow(sessionId, Set.empty)))
  when(flowRepository.saveFlow[NewApplicationEmailPreferencesFlowV2](isA[NewApplicationEmailPreferencesFlowV2])).thenReturn(Future.successful(NewApplicationEmailPreferencesFlowV2(
    sessionId,
    EmailPreferences.noPreferences,
    applicationId,
    Set.empty,
    Set.empty,
    Set.empty
  )))
  when(flowRepository.saveFlow[EmailPreferencesFlowV2](isA[EmailPreferencesFlowV2])).thenReturn(Future.successful(EmailPreferencesFlowV2(
    sessionId,
    Set(category),
    Map.empty,
    Set.empty,
    List.empty
  )))
  when(tpdConnector.fetchEmailForResetCode(*)(*)).thenReturn(Future.successful(userEmail))
  when(tpdConnector.requestReset(*[LaxEmailAddress])(*)).thenReturn(Future.successful(OK))
  when(tpdConnector.reset(*)(*)).thenReturn(Future.successful(OK))
  when(tpdConnector.authenticate(*)(*)).thenReturn(Future.successful(UserAuthenticationResponse(false, false, None, Some(session))))
  when(tpdConnector.fetchSession(eqTo(sessionId))(*)).thenReturn(Future.successful(session))
  when(tpdConnector.deleteSession(eqTo(sessionId))(*)).thenReturn(Future.successful(OK))
  when(tpdConnector.authenticateMfaAccessCode(*)(*)).thenReturn(Future.successful(session))
  when(tpdConnector.verify(*)(*)).thenReturn(Future.successful(OK))
  when(tpaProductionConnector.verify(*)(*)).thenReturn(Future.successful(ApplicationVerificationSuccessful))
  when(tpdConnector.resendVerificationEmail(*[LaxEmailAddress])(*)).thenReturn(Future.successful(OK))
  when(thirdPartyApplicationSubmissionsConnector.fetchResponsibleIndividualVerification(*[String])(*)).thenReturn(Future.successful(Some(responsibleIndividualVerification)))
  when(thirdPartyApplicationSubmissionsConnector.fetchLatestExtendedSubmission(*[ApplicationId])(*)).thenReturn(Future.successful(Some(extendedSubmission)))
  when(thirdPartyApplicationSubmissionsConnector.fetchSubmission(*[Submission.Id])(*)).thenReturn(Future.successful(Some(extendedSubmission)))
  when(thirdPartyApplicationSubmissionsConnector.fetchLatestSubmission(*[ApplicationId])(*)).thenReturn(Future.successful(Some(submission)))
  when(thirdPartyApplicationSubmissionsConnector.recordAnswer(*[Submission.Id], *[Question.Id], *[List[String]])(*)).thenReturn(Future.successful(Right(extendedSubmission)))
  when(thirdPartyApplicationSubmissionsConnector.createSubmission(*[ApplicationId], *[LaxEmailAddress])(*)).thenReturn(Future.successful(Some(submission)))
  when(apmConnector.fetchAllCombinedAPICategories()(*)).thenReturn(Future.successful(Right(List(APICategoryDisplayDetails("category", "name")))))
  when(tpdConnector.fetchDeveloper(*[UserId])(*)).thenReturn(Future.successful(Some(developer)))
  when(tpdConnector.updateProfile(*[UserId], *[UpdateProfileRequest])(*)).thenReturn(Future.successful(1))
  when(tpdConnector.updateEmailPreferences(*[UserId], *[EmailPreferences])(*)).thenReturn(Future.successful(true))
  when(tpdConnector.removeEmailPreferences(*[UserId])(*)).thenReturn(Future.successful(true))
  when(apmConnector.fetchCombinedApisVisibleToUser(*[UserId])(*)).thenReturn(Future.successful(Right(List(CombinedApi("my service", "display name", List.empty, REST_API)))))
  when(tpdConnector.changePassword(*[ChangePassword])(*)).thenReturn(Future.successful(1))
  when(thirdPartyDeveloperMfaConnector.verifyMfa(*[UserId], *[MfaId], *[String])(*)).thenReturn(Future.successful(true))
  when(thirdPartyDeveloperMfaConnector.removeMfaById(*[UserId], *[MfaId])(*)).thenReturn(Future.successful(()))
  when(thirdPartyDeveloperMfaConnector.createMfaAuthApp(*[UserId])(*)).thenReturn(Future.successful(registerAuthAppResponse))
  when(thirdPartyDeveloperMfaConnector.changeName(*[UserId], *[MfaId], *[String])(*)).thenReturn(Future.successful(true))
  when(thirdPartyDeveloperMfaConnector.createMfaSms(*[UserId], *[String])(*)).thenReturn(Future.successful(registerSmsResponse))
  when(thirdPartyDeveloperMfaConnector.sendSms(*[UserId], *[MfaId])(*)).thenReturn(Future.successful(true))
  when(thirdPartyDeveloperDynamicsConnector.getTickets()(*)).thenReturn(Future.successful(List.empty))
  when(thirdPartyDeveloperDynamicsConnector.createTicket(*[String], *[String], *[String])(*)).thenReturn(Future.successful(Right(())))

  private def populatePathTemplateWithValues(pathTemplate: String, values: Map[String, String]): String = {
    // TODO fail test if path contains parameters that aren't supplied by the values map
    values.foldLeft(pathTemplate)((path: String, kv: (String, String)) => path.replace(s":${kv._1}", kv._2).replace(s"*${kv._1}", kv._2))
  }

  private def getQueryParameterString(requestValues: RequestValues): String = {
    requestValues.queryParams.map(kv => s"${kv._1}=${kv._2}").mkString("&")
  }

  private def buildRequestPath(requestValues: RequestValues): String = {
    val populatedPathTemplate = populatePathTemplateWithValues(requestValues.endpoint.pathTemplate, requestValues.pathValues)
    val queryParameterString  = getQueryParameterString(requestValues)

    s"$populatedPathTemplate${if (queryParameterString.isEmpty) "" else s"?$queryParameterString"}"
  }

  def describeScenario() = s"$describeApplication, $describeAppState, $describeDeployment, $describeUserRole, $describeAuthenticationState"

  def getExpectedResponse(endpoint: Endpoint): Response

  def callEndpoint(requestValues: RequestValues): Response = {
    try {
      val path    = buildRequestPath(requestValues)
      val request = updateRequestForScenario(FakeRequest(requestValues.endpoint.verb, path))

      val result = (requestValues.postBody.isEmpty match {
        case false => route(app, CSRFTokenHelper.addCSRFToken(request.withFormUrlEncodedBody(requestValues.postBody.toSeq: _*)))
        case true  => route(app, CSRFTokenHelper.addCSRFToken(request))
      }).get

      status(result) match {
        case status: Int if 200 to 299 contains status => Success()
        case status: Int if 300 to 399 contains status => Redirect(redirectLocation(result).get)
        case 400                                       => BadRequest()
        case 401                                       => Unauthorized()
        case 403                                       => Forbidden()
        case 404                                       => NotFound()
        case 423                                       => Locked()
        case status                                    => Unexpected(status)
      }
    } catch {
      case e: Exception => Error(e.toString)
    }
  }

  final def getPathParameterValues(): Map[String, String] = Map(
    "id"                     -> applicationId.text,
    "aid"                    -> applicationId.text,
    "qid"                    -> question.id.value,
    "sid"                    -> submissionId.value,
    "environment"            -> Environment.PRODUCTION.entryName,
    "pageNumber"             -> "1",
    "context"                -> apiContext.value,
    "version"                -> apiVersion.value,
    "saveSubsFieldsPageMode" -> "lefthandnavigation",
    "fieldName"              -> apiFieldName.value,
    "addTeamMemberPageMode"  -> "applicationcheck",
    "teamMemberHash"         -> "b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514",
    "file"                   -> "javascripts/subscriptions.js",
    "clientSecretId"         -> "s1id"
  )

  final def getQueryParameterValues(endpoint: Endpoint): Map[String, String] = {
    endpoint match {
      case Endpoint("GET", "/developer/applications/:id/change-locked-subscription", _)           =>
        Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("POST", "/developer/applications/:id/change-locked-subscription", _)          =>
        Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("GET", "/developer/applications/:id/change-private-subscription", _)          =>
        Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("POST", "/developer/applications/:id/change-private-subscription", _)         =>
        Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("POST", "/developer/applications/:id/change-subscription", _)                 => Map("context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("GET", "/developer/applications/:id/ip-allowlist/remove", _)                  => Map("cidrBlock" -> "192.168.1.2/8")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/remove", _)                 => Map("cidrBlock" -> "192.168.1.2/8")
      case Endpoint("GET", "/developer/verification", _)                                          => Map("code" -> "CODE123")
      case Endpoint("GET", "/developer/login-mfa", _)                                             => Map("mfaId" -> authAppMfaId.value.toString, "mfaType" -> MfaType.AUTHENTICATOR_APP.toString)
      case Endpoint("POST", "/developer/login-mfa", _)                                            =>
        Map("mfaId" -> authAppMfaId.value.toString, "mfaType" -> MfaType.AUTHENTICATOR_APP.toString, "userHasMultipleMfa" -> false.toString)
      case Endpoint("GET", "/developer/reset-password-link", _)                                   => Map("code" -> "1324")
      case Endpoint("GET", "/developer/application-verification", _)                              => Map("code" -> "1324")
      case Endpoint("GET", "/developer/profile/email-preferences/apis", _)                        => Map("category" -> "AGENTS")
      case Endpoint(_, "/developer/submissions/responsible-individual-verification", _)           => Map("code" -> "code123")
      case Endpoint(_, "/developer/profile/email-preferences/topics-from-subscriptions", _)       => Map("applicationId" -> applicationId.text)
      case Endpoint(_, "/developer/profile/email-preferences/apis-from-subscriptions", _)         => Map("applicationId" -> applicationId.text)
      case Endpoint("POST", "/developer/profile/email-preferences/no-apis-from-subscriptions", _) => Map("applicationId" -> applicationId.text)
      case Endpoint("GET", "/developer/profile/security-preferences/auth-app/access-code", _)     =>
        Map("mfaId" -> authAppMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString, "mfaIdForRemoval" -> authAppMfaId.value.toString)
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/access-code", _)    =>
        Map("mfaId" -> authAppMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString, "mfaIdForRemoval" -> authAppMfaId.value.toString)
      case Endpoint("GET", "/developer/profile/security-preferences/auth-app/name", _)            => Map("mfaId" -> authAppMfaId.value.toString)
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/name", _)           => Map("mfaId" -> authAppMfaId.value.toString)
      case Endpoint("GET", "/developer/profile/security-preferences/sms/access-code", _)          =>
        Map("mfaId" -> smsMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString, "mfaIdForRemoval" -> smsMfaId.value.toString)
      case Endpoint("POST", "/developer/profile/security-preferences/sms/access-code", _)         =>
        Map("mfaId" -> smsMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString, "mfaIdForRemoval" -> authAppMfaId.value.toString)
      case Endpoint("GET", "/developer/profile/security-preferences/remove-mfa", _)               => Map("mfaId" -> authAppMfaId.value.toString, "mfaType" -> MfaType.AUTHENTICATOR_APP.toString)
      case Endpoint("GET", "/developer/profile/security-preferences/select-mfa", _)               => Map("mfaId" -> authAppMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString)
      case Endpoint("POST", "/developer/profile/security-preferences/select-mfa", _)              => Map("mfaId" -> authAppMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString)
      case Endpoint("GET", "/developer/login/select-mfa", _)                                      => Map("authAppMfaId" -> authAppMfaId.value.toString, "smsMfaId" -> smsMfaId.value.toString)

      case _ => Map.empty
    }
  }

  final def getBodyParameterValues(endpoint: Endpoint): Map[String, String] = {
    endpoint match {
      case Endpoint("POST", "/developer/registration", _)                                                                              =>
        Map("firstname" -> userFirstName, "lastname" -> userLastName, "emailaddress" -> userEmail.text, "password" -> userPassword, "confirmpassword" -> userPassword)
      case Endpoint("POST", "/developer/login", _)                                                                                     => Map("emailaddress" -> userEmail.text, "password" -> userPassword)
      case Endpoint("POST", "/developer/forgot-password", _)                                                                           => Map("emailaddress" -> userEmail.text)
      case Endpoint("POST", "/developer/login-mfa", _)                                                                                 => Map("accessCode" -> "123456", "rememberMe" -> "false")
      case Endpoint("POST", "/developer/reset-password", _)                                                                            => Map("password" -> userPassword, "confirmpassword" -> userPassword)
      case Endpoint("POST", "/developer/support", _)                                                                                   => Map("fullname" -> userFullName, "emailaddress" -> userEmail.text, "comments" -> "I am very cross about something")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/terms-and-conditions", _)                                  =>
        Map("hasUrl" -> "true", "termsAndConditionsURL" -> "https://example.com/tcs")
      case Endpoint("POST", "/developer/applications/:id/team-members/add/:addTeamMemberPageMode", _)                                  => Map("email" -> userEmail.text, "role" -> "developer")
      case Endpoint("POST", "/developer/applications/:id/team-members/remove", _)                                                      => Map("email" -> userEmail.text, "confirm" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/details/change-app-name", _)                                                  => Map("applicationName" -> ("new " + applicationName))
      case Endpoint("POST", "/developer/applications/:id/details/change-privacy-policy-location", _)                                   =>
        Map("privacyPolicyUrl" -> "http://example.com", "isInDesktop" -> "false", "isNewJourney" -> "true")
      case Endpoint("POST", "/developer/applications/:id/details/change-terms-conditions-location", _)                                 =>
        Map("termsAndConditionsUrl" -> "http://example.com", "isInDesktop" -> "false", "isNewJourney" -> "true")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/add", _)                                                        => Map("redirectUri" -> "https://example.com/redirect")
      case Endpoint("POST", "/developer/applications/:id/details/terms-of-use", _)                                                     => Map("termsOfUseAgreed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/change-confirmation", _)                                        =>
        Map("originalRedirectUri" -> redirectUrl, "newRedirectUri" -> (redirectUrl + "-new"))
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/delete", _)                                                     => Map("redirectUri" -> redirectUrl, "deleteRedirectConfirm" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/delete-principal", _)                                                         => Map("deleteConfirm" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/add", _)                                                         => Map("ipAddress" -> "1.2.3.4/24")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/change", _)                                                      => Map("confirm" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/self-or-other", _)                              => Map("who" -> "self")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/other", _)                                      =>
        Map("name" -> (responsibleIndividual.fullName.value + " new"), "email" -> ("new" + responsibleIndividual.emailAddress.text))
      case Endpoint("POST", "/developer/applications/:id/change-subscription", _)                                                      => Map("subscribed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/change-locked-subscription", _)                                               => Map("subscribed" -> "true", "confirm" -> "true")
      case Endpoint("POST", "/developer/applications/:id/change-private-subscription", _)                                              => Map("subscribed" -> "true", "confirm" -> "true")
      case Endpoint("POST", "/developer/applications/:id/add/subscription-configuration/:pageNumber", _)                               => Map(apiFieldName.value -> apiFieldValue.value)
      case Endpoint("POST", "/developer/applications/:id/api-metadata/:context/:version/:saveSubsFieldsPageMode", _)                   => Map(apiFieldName.value -> apiFieldValue.value)
      case Endpoint("POST", "/developer/applications/:id/api-metadata/:context/:version/fields/:fieldName/:saveSubsFieldsPageMode", _) =>
        Map(apiFieldName.value -> apiFieldValue.value)
      case Endpoint("POST", "/developer/no-applications", _)                                                                           => Map("choice" -> "use-apis")
      case Endpoint("POST", "/developer/applications/:id/change-api-subscriptions", _)                                                 => Map("ctx-1_0-subscribed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/sell-resell-or-distribute-your-software", _)                                  => Map("answer" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/request-check", _)                                                            => Map(
          "apiSubscriptionsComplete"              -> "true",
          "apiSubscriptionConfigurationsComplete" -> "true",
          "contactDetailsComplete"                -> "true",
          "teamConfirmedComplete"                 -> "true",
          "confirmedNameComplete"                 -> "true",
          "providedPrivacyPolicyURLComplete"      -> "true",
          "providedTermsAndConditionsURLComplete" -> "true",
          "termsOfUseAgreementComplete"           -> "true"
        )
      case Endpoint("POST", "/developer/applications/:id/request-check/terms-and-conditions", _)                                       => Map("hasUrl" -> "true", "termsAndConditionsURL" -> "https://example.com/tcs")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/contact", _)                                               => Map("fullname" -> userFullName, "email" -> userEmail.text, "telephone" -> userPhone)
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/name", _)                                                  => Map("applicationName" -> applicationName)
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/privacy-policy", _)                                        => Map("hasUrl" -> "true", "privacyPolicyURL" -> "https://example.com/priv")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/subscriptions", _)                                         => Map()
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/team/remove", _)                                           => Map("email" -> userEmail.text)
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/terms-of-use", _)                                          => Map("termsOfUseAgreed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/request-check/contact", _)                                                    => Map("fullname" -> userFullName, "email" -> userEmail.text, "telephone" -> userPhone)
      case Endpoint("POST", "/developer/applications/:id/request-check/name", _)                                                       => Map("applicationName" -> applicationName)
      case Endpoint("POST", "/developer/applications/:id/request-check/privacy-policy", _)                                             => Map("hasUrl" -> "true", "privacyPolicyURL" -> "https://example.com/priv")
      case Endpoint("POST", "/developer/applications/:id/request-check/team/remove", _)                                                => Map("email" -> userEmail.text)
      case Endpoint("POST", "/developer/applications/:id/request-check/terms-of-use", _)                                               => Map("termsOfUseAgreed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/details/change", _)                                                           => Map(
          "applicationId"         -> applicationId.text,
          "applicationName"       -> applicationName,
          "description"           -> "my description",
          "privacyPolicyUrl"      -> privacyPolicyUrl,
          "termsAndConditionsUrl" -> termsConditionsUrl,
          "grantLength"           -> "1"
        )
      case Endpoint("POST", "/developer/applications/add/switch", _)                                                                   => Map("applicationId" -> applicationId.text)
      case Endpoint("POST", "/developer/profile/email-preferences/topics-from-subscriptions", _)                                       => Map("topic[]" -> "BUSINESS_AND_POLICY", "applicationId" -> applicationId.text)
      case Endpoint("POST", "/developer/submissions/responsible-individual-verification", _)                                           => Map("verified" -> "yes")
      case Endpoint("POST", "/developer/profile/delete", _)                                                                            => Map("confirmation" -> "true")
      case Endpoint("POST", "/developer/profile/email-preferences/topics", _)                                                          => Map("topic[]" -> "BUSINESS_AND_POLICY")
      case Endpoint("POST", "/developer/profile/", _)                                                                                  => Map("firstname" -> userFirstName, "lastname" -> userLastName, "organisation" -> organisation)
      case Endpoint("POST", "/developer/profile/email-preferences/apis-from-subscriptions", _)                                         => Map("selectedApi[]" -> "my api", "applicationId" -> applicationId.text)
      case Endpoint("POST", "/developer/profile/password", _)                                                                          =>
        Map("currentpassword" -> userPassword, "password" -> (userPassword + "new"), "confirmpassword" -> (userPassword + "new"))
      case Endpoint("POST", "/developer/profile/email-preferences/apis", _)                                                            => Map("apiRadio" -> "1", "selectedApi" -> "api1", "currentCategory" -> category)
      case Endpoint("POST", "/developer/profile/email-preferences/categories", _)                                                      => Map("taxRegime[]" -> "1")
      case Endpoint("POST", "/developer/submissions/:sid/question/:qid", _)                                                            => Map("submit-action" -> "acknowledgement")
      case Endpoint("POST", "/developer/submissions/:sid/question/:qid/update", _)                                                     => Map("submit-action" -> "acknowledgement")
      case Endpoint("POST", "/developer/submissions/application/:aid/cancel-request", _)                                               => Map("submit-action" -> "cancel-request")
      case Endpoint("POST", "/developer/profile/security-preferences/select-mfa", _)                                                   => Map("mfaType" -> "SMS")
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/access-code", _)                                         => Map("accessCode" -> "123456")
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/name", _)                                                => Map("name" -> "appName")
      case Endpoint("POST", "/developer/profile/security-preferences/sms/setup", _)                                                    => Map("mobileNumber" -> "0123456789")
      case Endpoint("POST", "/developer/profile/security-preferences/sms/access-code", _)                                              => Map("accessCode" -> "123456", "mobileNumber" -> "0123456789")
      case Endpoint("POST", "/developer/poc-dynamics/tickets/add", _)                                                                  => Map("customerId" -> "11111111-1111-1111-1111-111111111111", "title" -> "title", "description" -> "desc")
      case Endpoint("POST", "/developer/login/select-mfa", _)                                                                          => Map("mfaId" -> authAppMfaId.value.toString)
      case _                                                                                                                           => Map.empty
    }
  }

  def getEndpointSuccessResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("POST", "/developer/registration", _)                                                                              => Redirect("/developer/confirmation")
      case Endpoint("GET", "/developer/resend-verification", _)                                                                        => Redirect("/developer/confirmation")
      case Endpoint("GET", "/developer/login", _)                                                                                      => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/login", _)                                                                                     => Redirect("/developer/login/2sv-recommendation")
      case Endpoint("GET", "/developer/login/2SV-help", _)                                                                             => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/login/2SV-help", _)                                                                            => Redirect("/developer/applications")
      case Endpoint("GET", "/developer/login/2SV-help/complete", _)                                                                    => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/login-mfa", _)                                                                                 => Redirect("/developer/profile/security-preferences/sms/setup/reminder")
      case Endpoint("POST", "/developer/logout/survey", _)                                                                             => Redirect("/developer/logout")
      case Endpoint("GET", "/developer/locked", _)                                                                                     => Locked()
      case Endpoint("GET", "/developer/forgot-password", _)                                                                            => Redirect("/developer/applications")
      case Endpoint("GET", "/developer/reset-password-link", _)                                                                        => Redirect("/developer/reset-password")
      case Endpoint("POST", "/developer/support", _)                                                                                   => Redirect("/developer/support/submitted")
      case Endpoint("POST", "/developer/applications/:id/team-members/remove", _)                                                      => Redirect(s"/developer/applications/${applicationId.text}/team-members")
      case Endpoint("POST", "/developer/applications/:id/team-members/add/:addTeamMemberPageMode", _)                                  =>
        Redirect(s"/developer/applications/${applicationId.text}/request-check/team")
      case Endpoint("POST", "/developer/applications/:id/details/change-privacy-policy-location", _)                                   => Redirect(s"/developer/applications/${applicationId.text}/details")
      case Endpoint("POST", "/developer/applications/:id/details/change-terms-conditions-location", _)                                 => Redirect(s"/developer/applications/${applicationId.text}/details")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/delete-confirmation", _)                                        => Redirect(s"/developer/applications/${applicationId.text}/redirect-uris")
      case Endpoint("POST", "/developer/applications/:id/details/terms-of-use", _)                                                     => Redirect(s"/developer/applications/${applicationId.text}/details")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/add", _)                                                        => Redirect(s"/developer/applications/${applicationId.text}/redirect-uris")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/delete", _)                                                     => Redirect(s"/developer/applications/${applicationId.text}/redirect-uris")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/change-confirmation", _)                                        => Redirect(s"/developer/applications/${applicationId.text}/redirect-uris")
      case Endpoint("POST", "/developer/applications/:id/delete-principal", _)                                                         => Redirect(s"/developer/applications/${applicationId.text}/details")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/change", _)                                                      => Redirect(s"/developer/applications/${applicationId.text}/ip-allowlist/activate")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/add", _)                                                         => Redirect(s"/developer/applications/${applicationId.text}/ip-allowlist/change")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/remove", _)                                                      => Redirect(s"/developer/applications/${applicationId.text}/ip-allowlist/setup")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/self-or-other", _)                              =>
        Redirect(s"/developer/applications/${applicationId.text}/responsible-individual/change/self")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/self", _)                                       =>
        Redirect(s"/developer/applications/${applicationId.text}/responsible-individual/change/self/confirmed")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/other", _)                                      =>
        Redirect(s"/developer/applications/${applicationId.text}/responsible-individual/change/other/requested")
      case Endpoint("POST", "/developer/applications/:id/client-secret-new", _)                                                        => Success()
      case Endpoint("POST", "/developer/applications/:id/client-secret/:clientSecretId/delete", _)                                     => Redirect(s"/developer/applications/${applicationId.text}/client-secrets")
      case Endpoint("GET", "/developer/applications/:id/request-check/appDetails", _)                                                  => Redirect(s"/developer/applications/${applicationId.text}/request-check")
      case Endpoint("POST", "/developer/applications/:id/add/subscription-configuration/:pageNumber", _)                               =>
        Redirect(s"/developer/applications/${applicationId.text}/add/subscription-configuration-step/1")
      case Endpoint("GET", "/developer/applications/:id/add/subscription-configuration-step/:pageNumber", _)                           =>
        Redirect(s"/developer/applications/${applicationId.text}/request-check")
      case Endpoint("POST", "/developer/applications/:id/api-metadata/:context/:version/:saveSubsFieldsPageMode", _)                   =>
        Redirect(s"/developer/applications/${applicationId.text}/api-metadata")
      case Endpoint("POST", "/developer/applications/:id/api-metadata/:context/:version/fields/:fieldName/:saveSubsFieldsPageMode", _) =>
        Redirect(s"/developer/applications/${applicationId.text}/api-metadata")
      case Endpoint("POST", "/developer/no-applications", _)                                                                           => Redirect(s"/developer/no-applications-start")
      case Endpoint("POST", "/developer/applications/:id/confirm-subscriptions", _)                                                    =>
        Redirect(s"/developer/submissions/application/${applicationId.text}/production-credentials-checklist")
      case Endpoint("POST", "/developer/applications/:id/change-api-subscriptions", _)                                                 => Redirect(s"/developer/applications/${applicationId.text}/confirm-subscriptions")
      case Endpoint("POST", "/developer/applications/:id/sell-resell-or-distribute-your-software", _)                                  =>
        Redirect(s"/developer/applications/${applicationId.text}/confirm-subscriptions")
      case Endpoint("POST", "/developer/applications/:id/change-subscription", _)                                                      => Redirect(s"/developer/applications/${applicationId.text}/details")
      case Endpoint("GET", path, _) if path.startsWith("/developer/applications/:id/check-your-answers")                               => Success()
      case Endpoint("GET", path, _) if path.startsWith("/developer/applications/:id/request-check")                                    => Success()
      case Endpoint("POST", "/developer/applications/:id/check-your-answers", _)                                                       => Redirect(s"/developer/applications/${applicationId.text}/request-check/submitted")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/team", _)                                                  => Redirect(s"/developer/applications/${applicationId.text}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/request-check", _)                                                            => Redirect(s"/developer/applications/${applicationId.text}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/request-check/team", _)                                                       => Redirect(s"/developer/applications/${applicationId.text}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/terms-and-conditions", _)                                       => Redirect(s"/developer/applications/${applicationId.text}/request-check")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/terms-and-conditions", _)                                  =>
        Redirect(s"/developer/applications/${applicationId.text}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/contact", _)                                               => Redirect(s"/developer/applications/${applicationId.text}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/name", _)                                                  => Redirect(s"/developer/applications/${applicationId.text}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/terms-of-use", _)                                          => Redirect(s"/developer/applications/${applicationId.text}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/privacy-policy", _)                                        => Redirect(s"/developer/applications/${applicationId.text}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/subscriptions", _)                                         => Redirect(s"/developer/applications/${applicationId.text}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/team/remove", _)                                           => Redirect(s"/developer/applications/${applicationId.text}/check-your-answers/team")
      case Endpoint("POST", "/developer/applications/:id/request-check/subscriptions", _)                                              => Redirect(s"/developer/applications/${applicationId.text}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/contact", _)                                                    => Redirect(s"/developer/applications/${applicationId.text}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/name", _)                                                       => Redirect(s"/developer/applications/${applicationId.text}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/privacy-policy", _)                                             => Redirect(s"/developer/applications/${applicationId.text}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/team/remove", _)                                                => Redirect(s"/developer/applications/${applicationId.text}/request-check/team")
      case Endpoint("POST", "/developer/applications/:id/request-check/terms-of-use", _)                                               => Redirect(s"/developer/applications/${applicationId.text}/request-check")
      case Endpoint("POST", "/developer/applications/:id/details/change", _)                                                           => Redirect(s"/developer/applications/${applicationId.text}/details")
      case Endpoint("GET", "/developer/applications/:id/add/success", _)                                                               =>
        Redirect(s"/developer/profile/email-preferences/apis-from-subscriptions?applicationId=${applicationId.text}")
      case Endpoint("GET", "/developer/applications/add/:id", _)                                                                       => Redirect(s"/developer/applications/${applicationId.text}/before-you-start")
      case Endpoint("GET", "/developer/applications/add/production", _)                                                                => Redirect(s"/developer/applications/${applicationId.text}/before-you-start")
      case Endpoint(_, "/developer/applications/add/switch", _)                                                                        => Redirect(s"/developer/applications/${applicationId.text}/before-you-start")
      case Endpoint("POST", "/developer/profile/email-preferences/topics-from-subscriptions", _)                                       => Redirect(s"/developer/applications/${applicationId.text}/add/success")
      case Endpoint("POST", "/developer/profile/email-preferences/topics", _)                                                          => Redirect(s"/developer/profile/email-preferences")
      case Endpoint("POST", "/developer/profile/email-preferences/no-apis-from-subscriptions", _)                                      =>
        Redirect(s"/developer/profile/email-preferences/topics-from-subscriptions?applicationId=${applicationId.text}")
      case Endpoint("POST", "/developer/profile/email-preferences/unsubscribe", _)                                                     => Redirect(s"/developer/profile/email-preferences")
      case Endpoint("POST", "/developer/profile/email-preferences/no-categories", _)                                                   => Redirect(s"/developer/profile/email-preferences/topics")
      case Endpoint("POST", "/developer/profile/email-preferences/apis", _)                                                            => Redirect(s"/developer/profile/email-preferences/topics")
      case Endpoint("POST", "/developer/profile/email-preferences/apis-from-subscriptions", _)                                         =>
        Redirect(s"/developer/profile/email-preferences/topics-from-subscriptions?applicationId=${applicationId.text}")
      case Endpoint("POST", "/developer/profile/email-preferences/categories", _)                                                      => Redirect(s"/developer/profile/email-preferences/apis?category=$category")
      case Endpoint("POST", "/developer/submissions/:sid/question/:qid", _)                                                            =>
        Redirect(s"/developer/submissions/application/${applicationId.text}/production-credentials-checklist")
      case Endpoint("POST", "/developer/submissions/:sid/question/:qid/update", _)                                                     => Redirect(s"/developer/submissions/application/${applicationId.text}/check-answers")
      case Endpoint("POST", "/developer/profile/security-preferences/select-mfa", _)                                                   => Redirect(s"/developer/profile/security-preferences/sms/setup")
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/access-code", _)                                         =>
        Redirect(s"/developer/profile/security-preferences/auth-app/name?mfaId=${authAppMfaId.value.toString}")
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/name", _)                                                => Redirect(s"/developer/profile/security-preferences/auth-app/setup/complete")
      case Endpoint("POST", "/developer/profile/security-preferences/sms/setup", _)                                                    =>
        Redirect(s"/developer/profile/security-preferences/sms/access-code?mfaId=${smsMfaId.value.toString}&mfaAction=CREATE")
      case Endpoint("POST", "/developer/profile/security-preferences/sms/access-code", _)                                              => Redirect(s"/developer/profile/security-preferences/sms/setup/complete")
      case Endpoint("GET", "/developer/profile/security-preferences/remove-mfa", _)                                                    =>
        Redirect(s"/developer/profile/security-preferences/auth-app/access-code?mfaId=${authAppMfaId.value.toString}&mfaAction=REMOVE&mfaIdForRemoval=${authAppMfaId.value.toString}")
      case Endpoint("POST", "/developer/poc-dynamics/tickets/add", _)                                                                  => Redirect("/developer/poc-dynamics/tickets")
      case Endpoint("POST", "/developer/login/select-mfa", _)                                                                          => Redirect(s"/developer/login-mfa?mfaId=${authAppMfaId.value.toString}&mfaType=${MfaType.AUTHENTICATOR_APP.toString}")
      case Endpoint("GET", "/developer/login/select-mfa/try-another-option", _)                                                        => Unexpected(500)
      case Endpoint("GET", "/developer/applications/terms-of-use", _)                                                                  => Success()
      case _                                                                                                                           => Success()

    }
  }

  // Override these methods within scenarios classes
  override def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = request
  def getPathParameterValueOverrides(endpoint: Endpoint)                            = Map.empty[String, String]
  def getQueryParameterValueOverrides(endpoint: Endpoint)                           = Map.empty[String, String]
  def getBodyParameterValueOverrides(endpoint: Endpoint)                            = Map.empty[String, String]

  def populateRequestValues(endpoint: Endpoint): Seq[RequestValues] = {
    val pathParameterValues  = getPathParameterValues() ++ getPathParameterValueOverrides(endpoint)
    val queryParameterValues = getQueryParameterValues(endpoint) ++ getQueryParameterValueOverrides(endpoint)
    val bodyParameterValues  = getBodyParameterValues(endpoint) ++ getBodyParameterValueOverrides(endpoint)

    List(RequestValues(endpoint, pathParameterValues, queryParameterValues, bodyParameterValues))
  }

  val routesFilePrefixes = List(
    ("app", "/developer"),
    ("profile", "/developer/profile"),
    ("submissions", "/developer/submissions")
  )

  s"Test all endpoints using ${describeScenario()}" should {
    routesFilePrefixes
      .flatMap(routesFilePrefixDetails => {
        val (routesFilePrefix, pathPrefix) = routesFilePrefixDetails
        Source.fromFile(s"conf/$routesFilePrefix.routes").getLines().flatMap(line => parseEndpoint(line, pathPrefix))
      })
      .flatMap(populateRequestValues)
      .toSet foreach { requestValues: RequestValues =>
      {
        val expectedResponse = getExpectedResponse(requestValues.endpoint)
        s"expect response $expectedResponse when calling\n\t$requestValues" taggedAs ExcludeFromCoverage in {
          val result = callEndpoint(requestValues)
          withClue(s"failed because the actual response") {
            result shouldBe expectedResponse
          }
        }
      }
    }
  }

}
