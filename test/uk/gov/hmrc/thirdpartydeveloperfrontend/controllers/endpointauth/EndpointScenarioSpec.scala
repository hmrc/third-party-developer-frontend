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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Mode
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.test.Helpers.{redirectLocation, route, status}
import play.api.test.{CSRFTokenHelper, FakeRequest, Writeables}
import uk.gov.hmrc.apiplatform.modules.dynamics.connectors.ThirdPartyDeveloperDynamicsConnector
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.models.{MfaAction, MfaId, MfaType}
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{Question, Submission}
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.{ApiSubscriptions, GetProductionCredentialsFlow}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.{ApplicationUpdateSuccessful, ApplicationUpliftSuccessful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationNameValidationJson.ApplicationNameValidationResult
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{AddTeamMemberRequest, ChangePassword, CombinedApi, TicketCreated, UserAuthenticationResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{UpdateProfileRequest, User, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.{APICategoryDisplayDetails, EmailPreferences}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{EmailPreferencesFlowV2, IpAllowlistFlow, NewApplicationEmailPreferencesFlowV2}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SaveSubscriptionFieldsSuccessResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.Fields
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.io.Source

object EndpointScenarioSpec {
  def parseEndpoint(text: String, pathPrefix: String): Option[Endpoint] = {
    text.trim.split("\\s+", 3) match {
      case Array(verb, path, _) if verb.matches("[A-Z]+") => Some(Endpoint(verb, pathPrefix + path))
      case _ => None
    }
  }
}

abstract class EndpointScenarioSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Writeables
  with MockConnectors
  with HasApplication
  with HasUserWithRole
  with HasUserSession
  with UpdatesRequest
  {
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
  when(tpaSandboxConnector.fetchCredentials(*[ApplicationId])(*)).thenReturn(Future.successful(ApplicationToken(List(ClientSecret("s1id", "s1name", LocalDateTime.now(), None)), "secret")))
  when(tpaProductionConnector.fetchCredentials(*[ApplicationId])(*)).thenReturn(Future.successful(ApplicationToken(List(ClientSecret("s1id", "s1name", LocalDateTime.now(), None)), "secret")))
  when(sandboxPushPullNotificationsConnector.fetchPushSecrets(*[ClientId])(*)).thenReturn(Future.successful(List("secret1")))
  when(productionPushPullNotificationsConnector.fetchPushSecrets(*[ClientId])(*)).thenReturn(Future.successful(List("secret1")))
  when(tpdConnector.fetchByEmails(*[Set[String]])(*)).thenReturn(Future.successful(List(User(userEmail, Some(true)))))
  when(tpaSandboxConnector.removeTeamMember(*[ApplicationId], *[String], *[String], *[Set[String]])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.validateName(*[String], *[Option[ApplicationId]])(*)).thenReturn(Future.successful(Valid))
  when(tpaProductionConnector.applicationUpdate(*[ApplicationId],*[ApplicationUpdate])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.applicationUpdate(*[ApplicationId],*[ApplicationUpdate])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.updateApproval(*[ApplicationId],*[CheckInformation])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.updateApproval(*[ApplicationId],*[CheckInformation])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.update(*[ApplicationId],*[UpdateApplicationRequest])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.update(*[ApplicationId],*[UpdateApplicationRequest])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.updateIpAllowlist(*[ApplicationId],*[Boolean], *[Set[String]])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaProductionConnector.updateIpAllowlist(*[ApplicationId],*[Boolean], *[Set[String]])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(tpaSandboxConnector.addClientSecrets(*[ApplicationId], *[ClientSecretRequest])(*)).thenReturn(Future.successful(("1","2")))
  when(tpaProductionConnector.addClientSecrets(*[ApplicationId], *[ClientSecretRequest])(*)).thenReturn(Future.successful(("1","2")))
  when(apmConnector.addTeamMember(*[ApplicationId],*[AddTeamMemberRequest])(*)).thenReturn(Future.successful(()))
  when(apmConnector.subscribeToApi(*[ApplicationId],*[ApiIdentifier])(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))
  when(productionSubsFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *[Fields.Alias])(*)).thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))
  when(sandboxSubsFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *[Fields.Alias])(*)).thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))
  when(tpaSandboxConnector.validateName(*[String],*[Option[ApplicationId]])(*)).thenReturn(Future.successful(ApplicationNameValidation(ApplicationNameValidationResult(None))))
  when(apmConnector.upliftApplicationV2(*[ApplicationId], *[UpliftData])(*)).thenAnswer((appId: ApplicationId, _: UpliftData) => Future.successful(appId))
  when(apmConnector.fetchUpliftableApiIdentifiers(*)).thenReturn(Future.successful(Set(apiIdentifier)))
  when(apmConnector.fetchAllApis(*)(*)).thenReturn(Future.successful(Map.empty))
  when(apmConnector.fetchUpliftableSubscriptions(*[ApplicationId])(*)).thenReturn(Future.successful(Set(ApiIdentifier(apiContext, apiVersion))))
  when(tpaProductionConnector.requestUplift(*[ApplicationId], *[UpliftRequest])(*)).thenReturn(Future.successful(ApplicationUpliftSuccessful))
  when(deskproConnector.createTicket(*)(*)).thenReturn(Future.successful(TicketCreated))
  when(flowRepository.updateLastUpdated(*)).thenReturn(Future.successful(()))

  import scala.reflect.runtime.universe._
  import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows._

  def mockFetchBySessionIdAndFlowType[A <: Flow](a: A)(implicit tt: TypeTag[A]) = {
    when(flowRepository.fetchBySessionIdAndFlowType[A](*[String])(eqTo(tt),*)).thenReturn(
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
  when(flowRepository.deleteBySessionIdAndFlowType(*,*)).thenReturn(Future.successful(true))
  when(flowRepository.saveFlow[GetProductionCredentialsFlow](isA[GetProductionCredentialsFlow])).thenReturn(Future.successful(GetProductionCredentialsFlow(sessionId, None, None)))
  when(flowRepository.saveFlow[IpAllowlistFlow](isA[IpAllowlistFlow])).thenReturn(Future.successful(IpAllowlistFlow(sessionId, Set.empty)))
  when(flowRepository.saveFlow[NewApplicationEmailPreferencesFlowV2](isA[NewApplicationEmailPreferencesFlowV2])).thenReturn(Future.successful(NewApplicationEmailPreferencesFlowV2(sessionId, EmailPreferences.noPreferences, applicationId, Set.empty, Set.empty, Set.empty)))
  when(flowRepository.saveFlow[EmailPreferencesFlowV2](isA[EmailPreferencesFlowV2])).thenReturn(Future.successful(EmailPreferencesFlowV2(sessionId, Set(category), Map.empty, Set.empty, List.empty)))
  when(tpdConnector.fetchEmailForResetCode(*)(*)).thenReturn(Future.successful(userEmail))
  when(tpdConnector.requestReset(*)(*)).thenReturn(Future.successful(OK))
  when(tpdConnector.reset(*)(*)).thenReturn(Future.successful(OK))
  when(tpdConnector.authenticate(*)(*)).thenReturn(Future.successful(UserAuthenticationResponse(false, false, None, Some(session))))
  when(tpdConnector.fetchSession(eqTo(sessionId))(*)).thenReturn(Future.successful(session))
  when(tpdConnector.deleteSession(eqTo(sessionId))(*)).thenReturn(Future.successful(OK))
  when(tpdConnector.authenticateMfaAccessCode(*)(*)).thenReturn(Future.successful(session))
  when(tpdConnector.verify(*)(*)).thenReturn(Future.successful(OK))
  when(tpaProductionConnector.verify(*)(*)).thenReturn(Future.successful(ApplicationVerificationSuccessful))
  when(tpdConnector.resendVerificationEmail(*)(*)).thenReturn(Future.successful(OK))
  when(tpaSandboxConnector.deleteApplication(*[ApplicationId])(*)).thenReturn(Future.successful(()))
  when(tpaProductionConnector.deleteApplication(*[ApplicationId])(*)).thenReturn(Future.successful(()))
  when(thirdPartyApplicationSubmissionsConnector.fetchResponsibleIndividualVerification(*[String])(*)).thenReturn(Future.successful(Some(responsibleIndividualVerification)))
  when(thirdPartyApplicationSubmissionsConnector.fetchLatestExtendedSubmission(*[ApplicationId])(*)).thenReturn(Future.successful(Some(extendedSubmission)))
  when(thirdPartyApplicationSubmissionsConnector.fetchSubmission(*[Submission.Id])(*)).thenReturn(Future.successful(Some(extendedSubmission)))
  when(thirdPartyApplicationSubmissionsConnector.fetchLatestSubmission(*[ApplicationId])(*)).thenReturn(Future.successful(Some(submission)))
  when(thirdPartyApplicationSubmissionsConnector.recordAnswer(*[Submission.Id], *[Question.Id], *[List[String]])(*)).thenReturn(Future.successful(Right(extendedSubmission)))
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

  private def populatePathTemplateWithValues(pathTemplate: String, values: Map[String,String]): String = {
    //TODO fail test if path contains parameters that aren't supplied by the values map
    values.foldLeft(pathTemplate)((path: String, kv: (String,String)) => path.replace(s":${kv._1}", kv._2).replace(s"*${kv._1}", kv._2))
  }

  private def getQueryParameterString(requestValues: RequestValues): String = {
    requestValues.queryParams.map(kv => s"${kv._1}=${kv._2}").mkString("&")
  }

  private def buildRequestPath(requestValues: RequestValues): String = {
    val populatedPathTemplate = populatePathTemplateWithValues(requestValues.endpoint.pathTemplate, requestValues.pathValues)
    val queryParameterString = getQueryParameterString(requestValues)

    s"$populatedPathTemplate${if (queryParameterString.isEmpty) "" else s"?$queryParameterString"}"
  }

  def describeScenario() = s"$describeApplication, $describeAppState, $describeDeployment, $describeUserRole, $describeAuthenticationState"

  def getExpectedResponse(endpoint: Endpoint): Response

  def callEndpoint(requestValues: RequestValues): Response = {
    try {
      val path = buildRequestPath(requestValues)
      val request = updateRequestForScenario(FakeRequest(requestValues.endpoint.verb, path))

      val result = (requestValues.postBody.isEmpty match {
        case false => route(app, CSRFTokenHelper.addCSRFToken(request.withFormUrlEncodedBody(requestValues.postBody.toSeq:_*)))
        case true => route(app, CSRFTokenHelper.addCSRFToken(request))
      }).get

      status(result) match {
        case status: Int if 200 to 299 contains status => Success()
        case status: Int if 300 to 399 contains status => Redirect(redirectLocation(result).get)
        case 400 => BadRequest()
        case 401 => Unauthorized()
        case 403 => Forbidden()
        case 404 => NotFound()
        case 423 => Locked()
        case status => Unexpected(status)
      }
    } catch {
      case e: Exception => Error(e.toString)
    }
  }

  final def getPathParameterValues(): Map[String,String] = Map(
    "id" -> applicationId.value,
    "aid" -> applicationId.value,
    "qid" -> question.id.value,
    "sid" -> submissionId.value,
    "environment" -> Environment.PRODUCTION.entryName,
    "pageNumber" -> "1",
    "context" -> apiContext.value,
    "version" -> apiVersion.value,
    "saveSubsFieldsPageMode"-> "lefthandnavigation",
    "fieldName"-> apiFieldName.value,
    "addTeamMemberPageMode" -> "applicationcheck",
    "teamMemberHash" -> "b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514",
    "file" -> "javascripts/subscriptions.js",
    "clientSecretId" -> "s1id"
  )

  final def getQueryParameterValues(endpoint: Endpoint): Map[String,String] = {
    endpoint match {
      case Endpoint("GET",  "/developer/applications/:id/change-locked-subscription") => Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("POST", "/developer/applications/:id/change-locked-subscription") => Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("GET",  "/developer/applications/:id/change-private-subscription") => Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("POST", "/developer/applications/:id/change-private-subscription") => Map("name" -> applicationName, "context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("POST", "/developer/applications/:id/change-subscription") => Map("context" -> apiContext.value, "version" -> apiVersion.value, "redirectTo" -> redirectUrl)
      case Endpoint("GET",  "/developer/applications/:id/ip-allowlist/remove") => Map("cidrBlock" -> "192.168.1.2/8")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/remove") => Map("cidrBlock" -> "192.168.1.2/8")
      case Endpoint("GET",  "/developer/verification") => Map("code" -> "CODE123")
      case Endpoint(_,      "/developer/login-totp") => Map("mfaId" -> mfaId.value.toString)
      case Endpoint("GET",  "/developer/reset-password-link") => Map("code" -> "1324")
      case Endpoint("GET",  "/developer/application-verification") => Map("code" -> "1324")
      case Endpoint("GET",  "/developer/profile/email-preferences/apis") => Map("category" -> "AGENTS")
      case Endpoint(_,      "/developer/submissions/responsible-individual-verification") => Map("code" -> "code123")
      case Endpoint("GET",  "/developer/profile/protect-account/access-code") => Map("mfaId" -> mfaId.value.toString)
      case Endpoint("POST", "/developer/profile/protect-account/enable") => Map("mfaId" -> mfaId.value.toString)
      case Endpoint(_,      "/developer/profile/protect-account/remove-by-id") => Map("mfaId" -> mfaId.value.toString)
      case Endpoint(_,      "/developer/profile/email-preferences/topics-from-subscriptions") => Map("context" -> applicationId.value)
      case Endpoint(_,      "/developer/profile/email-preferences/apis-from-subscriptions") => Map("context" -> applicationId.value)
      case Endpoint("POST", "/developer/profile/email-preferences/no-apis-from-subscriptions") => Map("context" -> applicationId.value)
      case Endpoint("GET",  "/developer/profile/security-preferences/auth-app/access-code") => Map("mfaId" -> mfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString, "mfaIdForRemoval" -> mfaId.value.toString)
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/access-code") => Map("mfaId" -> mfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString, "mfaIdForRemoval" -> mfaId.value.toString)
      case Endpoint("GET", "/developer/profile/security-preferences/auth-app/name") => Map("mfaId" -> mfaId.value.toString)
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/name") => Map("mfaId" -> mfaId.value.toString)
      case Endpoint("GET",  "/developer/profile/security-preferences/sms/access-code") => Map("mfaId" -> smsMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString, "mfaIdForRemoval" -> smsMfaId.value.toString)
      case Endpoint("POST",  "/developer/profile/security-preferences/sms/access-code") => Map("mfaId" -> smsMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString, "mfaIdForRemoval" -> smsMfaId.value.toString)
      case Endpoint("GET",  "/developer/profile/security-preferences/remove-mfa") => Map("mfaId" -> smsMfaId.value.toString, "mfaType" -> MfaType.SMS.toString)
      case Endpoint("GET",  "/developer/profile/security-preferences/select-mfa") => Map("mfaId" -> smsMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString)
      case Endpoint("POST",  "/developer/profile/security-preferences/select-mfa") => Map("mfaId" -> smsMfaId.value.toString, "mfaAction" -> MfaAction.CREATE.toString)

      case _ => Map.empty
    }
  }

  final def getBodyParameterValues(endpoint: Endpoint): Map[String,String] = {
    endpoint match {
      case Endpoint("POST", "/developer/registration") => Map("firstname" -> userFirstName, "lastname" -> userLastName, "emailaddress" -> userEmail, "password" -> userPassword, "confirmpassword" -> userPassword)
      case Endpoint("POST", "/developer/login") => Map("emailaddress" -> userEmail, "password" -> userPassword)
      case Endpoint("POST", "/developer/forgot-password") => Map("emailaddress" -> userEmail)
      case Endpoint("POST", "/developer/login-totp") => Map("accessCode" -> "123456", "rememberMe" -> "false")
      case Endpoint("POST", "/developer/reset-password") => Map("password" -> userPassword, "confirmpassword" -> userPassword)
      case Endpoint("POST", "/developer/support") => Map("fullname" -> userFullName, "emailaddress" -> userEmail, "comments" -> "I am very cross about something")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/terms-and-conditions") => Map("hasUrl" -> "true", "termsAndConditionsURL" -> "https://example.com/tcs")
      case Endpoint("POST", "/developer/applications/:id/team-members/add/:addTeamMemberPageMode") => Map("email"-> userEmail, "role" -> "developer")
      case Endpoint("POST", "/developer/applications/:id/team-members/remove") => Map("email"-> userEmail, "confirm" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/details/change-app-name") => Map("applicationName"-> ("new " + applicationName))
      case Endpoint("POST", "/developer/applications/:id/details/change-privacy-policy-location") => Map("privacyPolicyUrl" -> "http://example.com", "isInDesktop" -> "false", "isNewJourney" -> "true")
      case Endpoint("POST", "/developer/applications/:id/details/change-terms-conditions-location") => Map("termsAndConditionsUrl" -> "http://example.com", "isInDesktop" -> "false", "isNewJourney" -> "true")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/add") => Map("redirectUri" -> "https://example.com/redirect")
      case Endpoint("POST", "/developer/applications/:id/details/terms-of-use") => Map("termsOfUseAgreed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/change-confirmation") => Map("originalRedirectUri" -> redirectUrl, "newRedirectUri" -> (redirectUrl + "-new"))
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/delete") => Map("redirectUri" -> redirectUrl, "deleteRedirectConfirm" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/delete-principal") => Map("deleteConfirm" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/add") => Map("ipAddress" -> "1.2.3.4/24")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/change") => Map("confirm" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/self-or-other") => Map("who" -> "self")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/other") => Map("name" -> (responsibleIndividual.fullName.value + " new"), "email" -> ("new" + responsibleIndividual.emailAddress.value))
      case Endpoint("POST", "/developer/applications/:id/change-subscription") => Map("subscribed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/change-locked-subscription") => Map("subscribed" -> "true", "confirm" -> "true")
      case Endpoint("POST", "/developer/applications/:id/change-private-subscription") => Map("subscribed" -> "true", "confirm" -> "true")
      case Endpoint("POST", "/developer/applications/:id/add/subscription-configuration/:pageNumber") => Map(apiFieldName.value -> apiFieldValue.value)
      case Endpoint("POST", "/developer/applications/:id/api-metadata/:context/:version/:saveSubsFieldsPageMode") => Map(apiFieldName.value -> apiFieldValue.value)
      case Endpoint("POST", "/developer/applications/:id/api-metadata/:context/:version/fields/:fieldName/:saveSubsFieldsPageMode") => Map(apiFieldName.value -> apiFieldValue.value)
      case Endpoint("POST", "/developer/no-applications") => Map("choice" -> "use-apis")
      case Endpoint("POST", "/developer/applications/:id/change-api-subscriptions") => Map("ctx-1_0-subscribed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/sell-resell-or-distribute-your-software") => Map("answer" -> "yes")
      case Endpoint("POST", "/developer/applications/:id/request-check") => Map(
        "apiSubscriptionsComplete" -> "true",
        "apiSubscriptionConfigurationsComplete" -> "true",
        "contactDetailsComplete" -> "true",
        "teamConfirmedComplete" -> "true",
        "confirmedNameComplete" -> "true",
        "providedPrivacyPolicyURLComplete" -> "true",
        "providedTermsAndConditionsURLComplete" -> "true",
        "termsOfUseAgreementComplete" -> "true"
      )
      case Endpoint("POST", "/developer/applications/:id/request-check/terms-and-conditions") => Map("hasUrl" -> "true", "termsAndConditionsURL" -> "https://example.com/tcs")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/contact") => Map("fullname" -> userFullName, "email" -> userEmail, "telephone" -> userPhone)
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/name") => Map("applicationName" -> applicationName)
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/privacy-policy") => Map("hasUrl" -> "true", "privacyPolicyURL" -> "https://example.com/priv")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/subscriptions") => Map()
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/team/remove") => Map("email" -> userEmail)
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/terms-of-use") => Map("termsOfUseAgreed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/request-check/contact") => Map("fullname" -> userFullName, "email" -> userEmail, "telephone" -> userPhone)
      case Endpoint("POST", "/developer/applications/:id/request-check/name") => Map("applicationName" -> applicationName)
      case Endpoint("POST", "/developer/applications/:id/request-check/privacy-policy") => Map("hasUrl" -> "true", "privacyPolicyURL" -> "https://example.com/priv")
      case Endpoint("POST", "/developer/applications/:id/request-check/team/remove") => Map("email" -> userEmail)
      case Endpoint("POST", "/developer/applications/:id/request-check/terms-of-use") => Map("termsOfUseAgreed" -> "true")
      case Endpoint("POST", "/developer/applications/:id/details/change") => Map("applicationId" -> applicationId.value, "applicationName" -> applicationName, "description" -> "my description", "privacyPolicyUrl" -> privacyPolicyUrl, "termsAndConditionsUrl" -> termsConditionsUrl, "grantLength" -> "1")
      case Endpoint("POST", "/developer/applications/add/switch") => Map("applicationId" -> applicationId.value)
      case Endpoint("POST", "/developer/profile/email-preferences/topics-from-subscriptions") => Map("topic[]" -> "BUSINESS_AND_POLICY", "applicationId" -> applicationId.value)
      case Endpoint("POST", "/developer/submissions/responsible-individual-verification") => Map("verified" -> "yes")
      case Endpoint("POST", "/developer/profile/delete") => Map("confirmation" -> "true")
      case Endpoint("POST", "/developer/profile/email-preferences/topics") => Map("topic[]" -> "BUSINESS_AND_POLICY")
      case Endpoint("POST", "/developer/profile/") => Map("firstname" -> userFirstName, "lastname" -> userLastName, "organisation" -> organisation)
      case Endpoint("POST", "/developer/profile/protect-account/remove-by-id") => Map("accessCode" -> "123456")
      case Endpoint("POST", "/developer/profile/email-preferences/apis-from-subscriptions") => Map("selectedApi[]" -> "my api", "applicationId" -> applicationId.value)
      case Endpoint("POST", "/developer/profile/password") => Map("currentpassword" -> userPassword, "password" -> (userPassword + "new"), "confirmpassword" -> (userPassword + "new"))
      case Endpoint("POST", "/developer/profile/protect-account/enable") => Map("accessCode" -> "123456")
      case Endpoint("POST", "/developer/profile/email-preferences/apis") => Map("apiRadio" -> "1", "selectedApi" -> "api1", "currentCategory" -> category)
      case Endpoint("POST", "/developer/profile/email-preferences/categories") => Map("taxRegime[]" -> "1")
      case Endpoint("POST", "/developer/submissions/:sid/question/:qid") => Map("submit-action" -> "acknowledgement")
      case Endpoint("POST", "/developer/submissions/:sid/question/:qid/update") => Map("submit-action" -> "acknowledgement")
      case Endpoint("POST", "/developer/submissions/application/:aid/cancel-request") => Map("submit-action" -> "cancel-request")
      case Endpoint("POST", "/developer/profile/security-preferences/select-mfa") => Map("mfaType" -> "SMS")
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/access-code") => Map("accessCode" -> "123456")
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/name") => Map("name" -> "appName")
      case Endpoint("POST", "/developer/profile/security-preferences/sms/setup") => Map("mobileNumber" -> "0123456789")
      case Endpoint("POST", "/developer/profile/security-preferences/sms/access-code") => Map("accessCode" -> "123456", "mobileNumber" -> "0123456789")
      case Endpoint("POST", "/developer/poc-dynamics/tickets/add") => Map("customerId" -> "11111111-1111-1111-1111-111111111111", "title" -> "title", "description" -> "desc")
      case _ => Map.empty
    }
  }

  def getEndpointSuccessResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("POST", "/developer/registration") => Redirect("/developer/confirmation")
      case Endpoint("GET",  "/developer/resend-verification") => Redirect("/developer/confirmation")
      case Endpoint("GET",  "/developer/login") => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/login") => Redirect("/developer/login/2sv-recommendation")
      case Endpoint("GET",  "/developer/login/2SV-help") => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/login/2SV-help") => Redirect("/developer/applications")
      case Endpoint("GET",  "/developer/login/2SV-help/complete") => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/login-totp") => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/logout/survey") => Redirect("/developer/logout")
      case Endpoint("GET",  "/developer/locked") => Locked()
      case Endpoint("GET",  "/developer/forgot-password") => Redirect("/developer/applications")
      case Endpoint("GET",  "/developer/reset-password-link") => Redirect("/developer/reset-password")
      case Endpoint("POST", "/developer/support") => Redirect("/developer/support/submitted")
      case Endpoint("POST", "/developer/applications/:id/team-members/remove") => Redirect(s"/developer/applications/${applicationId.value}/team-members")
      case Endpoint("POST", "/developer/applications/:id/team-members/add/:addTeamMemberPageMode") => Redirect(s"/developer/applications/${applicationId.value}/request-check/team")
      case Endpoint("POST", "/developer/applications/:id/details/change-privacy-policy-location") => Redirect(s"/developer/applications/${applicationId.value}/details")
      case Endpoint("POST", "/developer/applications/:id/details/change-terms-conditions-location") => Redirect(s"/developer/applications/${applicationId.value}/details")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/delete-confirmation") => Redirect(s"/developer/applications/${applicationId.value}/redirect-uris")
      case Endpoint("POST", "/developer/applications/:id/details/terms-of-use") => Redirect(s"/developer/applications/${applicationId.value}/details")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/add") => Redirect(s"/developer/applications/${applicationId.value}/redirect-uris")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/delete") => Redirect(s"/developer/applications/${applicationId.value}/redirect-uris")
      case Endpoint("POST", "/developer/applications/:id/redirect-uris/change-confirmation") => Redirect(s"/developer/applications/${applicationId.value}/redirect-uris")
      case Endpoint("POST", "/developer/applications/:id/delete-principal") => Redirect(s"/developer/applications/${applicationId.value}/details")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/change") => Redirect(s"/developer/applications/${applicationId.value}/ip-allowlist/activate")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/add") => Redirect(s"/developer/applications/${applicationId.value}/ip-allowlist/change")
      case Endpoint("POST", "/developer/applications/:id/ip-allowlist/remove") => Redirect(s"/developer/applications/${applicationId.value}/ip-allowlist/setup")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/self-or-other") => Redirect(s"/developer/applications/${applicationId.value}/responsible-individual/change/self")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/self") => Redirect(s"/developer/applications/${applicationId.value}/responsible-individual/change/self/confirmed")
      case Endpoint("POST", "/developer/applications/:id/responsible-individual/change/other") => Redirect(s"/developer/applications/${applicationId.value}/responsible-individual/change/other/requested")
      case Endpoint("POST", "/developer/applications/:id/client-secret-new") => Redirect(s"/developer/applications/${applicationId.value}/client-secrets")
      case Endpoint("POST", "/developer/applications/:id/client-secret/:clientSecretId/delete") => Redirect(s"/developer/applications/${applicationId.value}/client-secrets")
      case Endpoint("GET",  "/developer/applications/:id/request-check/appDetails") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("POST", "/developer/applications/:id/add/subscription-configuration/:pageNumber") => Redirect(s"/developer/applications/${applicationId.value}/add/subscription-configuration-step/1")
      case Endpoint("GET",  "/developer/applications/:id/add/subscription-configuration-step/:pageNumber") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("POST", "/developer/applications/:id/api-metadata/:context/:version/:saveSubsFieldsPageMode") => Redirect(s"/developer/applications/${applicationId.value}/api-metadata")
      case Endpoint("POST", "/developer/applications/:id/api-metadata/:context/:version/fields/:fieldName/:saveSubsFieldsPageMode") => Redirect(s"/developer/applications/${applicationId.value}/api-metadata")
      case Endpoint("POST", "/developer/no-applications") => Redirect(s"/developer/no-applications-start")
      case Endpoint("POST", "/developer/applications/:id/confirm-subscriptions") => Redirect(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
      case Endpoint("POST", "/developer/applications/:id/change-api-subscriptions") => Redirect(s"/developer/applications/${applicationId.value}/confirm-subscriptions")
      case Endpoint("POST", "/developer/applications/:id/sell-resell-or-distribute-your-software") => Redirect(s"/developer/applications/${applicationId.value}/confirm-subscriptions")
      case Endpoint("POST", "/developer/applications/:id/change-subscription") => Redirect(s"/developer/applications/${applicationId.value}/details")
      case Endpoint("GET", path) if path.startsWith("/developer/applications/:id/check-your-answers") => Success()
      case Endpoint("GET", path) if path.startsWith("/developer/applications/:id/request-check") => Success()
      case Endpoint("POST", "/developer/applications/:id/check-your-answers") => Redirect(s"/developer/applications/${applicationId.value}/request-check/submitted")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/team") => Redirect(s"/developer/applications/${applicationId.value}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/request-check") => Redirect(s"/developer/applications/${applicationId.value}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/request-check/team") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/terms-and-conditions") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/terms-and-conditions") => Redirect(s"/developer/applications/${applicationId.value}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/contact") => Redirect(s"/developer/applications/${applicationId.value}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/name") => Redirect(s"/developer/applications/${applicationId.value}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/terms-of-use") => Redirect(s"/developer/applications/${applicationId.value}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/privacy-policy") => Redirect(s"/developer/applications/${applicationId.value}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/subscriptions") => Redirect(s"/developer/applications/${applicationId.value}/check-your-answers")
      case Endpoint("POST", "/developer/applications/:id/check-your-answers/team/remove") => Redirect(s"/developer/applications/${applicationId.value}/check-your-answers/team")
      case Endpoint("POST", "/developer/applications/:id/request-check/subscriptions") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/contact") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/name") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/privacy-policy") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("POST", "/developer/applications/:id/request-check/team/remove") => Redirect(s"/developer/applications/${applicationId.value}/request-check/team")
      case Endpoint("POST", "/developer/applications/:id/request-check/terms-of-use") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("POST", "/developer/applications/:id/details/change") => Redirect(s"/developer/applications/${applicationId.value}/details")
      case Endpoint("GET",  "/developer/applications/:id/add/success") => Redirect(s"/developer/profile/email-preferences/apis-from-subscriptions?context=${applicationId.value}")
      case Endpoint("GET",  "/developer/applications/add/:id") => Redirect(s"/developer/applications/${applicationId.value}/before-you-start")
      case Endpoint("GET",  "/developer/applications/add/production") => Redirect(s"/developer/applications/${applicationId.value}/before-you-start")
      case Endpoint(_,      "/developer/applications/add/switch") => Redirect(s"/developer/applications/${applicationId.value}/before-you-start")
      case Endpoint("POST", "/developer/profile/email-preferences/topics-from-subscriptions") => Redirect(s"/developer/applications/${applicationId.value}/add/success")
      case Endpoint("POST", "/developer/profile/email-preferences/topics") => Redirect(s"/developer/profile/email-preferences")
      case Endpoint("POST", "/developer/profile/email-preferences/no-apis-from-subscriptions") => Redirect(s"/developer/profile/email-preferences/topics-from-subscriptions?context=${applicationId.value}")
      case Endpoint("POST", "/developer/profile/email-preferences/unsubscribe") => Redirect(s"/developer/profile/email-preferences")
      case Endpoint("POST", "/developer/profile/email-preferences/no-categories") => Redirect(s"/developer/profile/email-preferences/topics")
      case Endpoint("POST", "/developer/profile/email-preferences/apis") => Redirect(s"/developer/profile/email-preferences/topics")
      case Endpoint("POST", "/developer/profile/protect-account/enable") => Redirect(s"/developer/profile/protect-account/complete")
      case Endpoint("POST", "/developer/profile/protect-account/remove-by-id") => Redirect(s"/developer/profile/protect-account/remove-by-id/complete")
      case Endpoint("POST", "/developer/profile/email-preferences/apis-from-subscriptions") => Redirect(s"/developer/profile/email-preferences/topics-from-subscriptions?context=${applicationId.value}")
      case Endpoint("POST", "/developer/profile/email-preferences/categories") => Redirect(s"/developer/profile/email-preferences/apis?category=${category}")
      case Endpoint("POST", "/developer/submissions/:sid/question/:qid") => Redirect(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
      case Endpoint("POST", "/developer/submissions/:sid/question/:qid/update") => Redirect(s"/developer/submissions/application/${applicationId.value}/check-answers")
      case Endpoint("POST", "/developer/profile/security-preferences/select-mfa") => Redirect(s"/developer/profile/security-preferences/sms/setup")
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/access-code") => Redirect(s"/developer/profile/security-preferences/auth-app/name?mfaId=${mfaId.value.toString}")
      case Endpoint("POST", "/developer/profile/security-preferences/auth-app/name") => Redirect(s"/developer/profile/security-preferences/auth-app/setup/complete")
      case Endpoint("POST", "/developer/profile/security-preferences/sms/setup") => Redirect(s"/developer/profile/security-preferences/sms/access-code?mfaId=${smsMfaId.value.toString}&mfaAction=CREATE")
      case Endpoint("POST", "/developer/profile/security-preferences/sms/access-code") => Redirect(s"/developer/profile/security-preferences/sms/setup/complete")
      case Endpoint("GET", "/developer/profile/security-preferences/remove-mfa") => Redirect(s"/developer/profile/security-preferences/sms/access-code?mfaId=${smsMfaId.value.toString}&mfaAction=REMOVE&mfaIdForRemoval=${smsMfaId.value.toString}")
      case Endpoint("POST", "/developer/poc-dynamics/tickets/add") => Redirect("/developer/poc-dynamics/tickets")
      case _ => Success()
    }
  }

  // Override these methods within scenarios classes
  override def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = request
  def getPathParameterValueOverrides(endpoint: Endpoint) = Map.empty[String,String]
  def getQueryParameterValueOverrides(endpoint: Endpoint) = Map.empty[String,String]
  def getBodyParameterValueOverrides(endpoint: Endpoint) = Map.empty[String,String]

  def populateRequestValues(endpoint: Endpoint): Seq[RequestValues] = {
    val pathParameterValues = getPathParameterValues() ++ getPathParameterValueOverrides(endpoint)
    val queryParameterValues = getQueryParameterValues(endpoint) ++ getQueryParameterValueOverrides(endpoint)
    val bodyParameterValues = getBodyParameterValues(endpoint) ++ getBodyParameterValueOverrides(endpoint)

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
        Source.fromFile(s"conf/${routesFilePrefix}.routes").getLines().flatMap(line => parseEndpoint(line, pathPrefix))
      })
      .flatMap(populateRequestValues(_))
      .toSet foreach { requestValues: RequestValues => {
        val expectedResponse = getExpectedResponse(requestValues.endpoint)
        s"return $expectedResponse for $requestValues" in {
          val result = callEndpoint(requestValues)
          withClue(s"Testing ${requestValues.endpoint}") {
            result shouldBe expectedResponse
          }
        }
      }
    }
  }

}
