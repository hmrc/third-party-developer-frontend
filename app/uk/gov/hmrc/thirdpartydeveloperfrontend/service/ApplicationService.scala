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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ApiDefinition}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{CheckInformation, Collaborator}
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{PrivacyPolicyLocation, TermsAndConditionsLocation}
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommand, ApplicationCommands}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketResult}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction.{AccountDeletionRequested, ApplicationDeletionRequested, Remove2SVRequested, UserLogoutSurveyCompleted}

@Singleton
class ApplicationService @Inject() (
    apmConnector: ApmConnector,
    connectorWrapper: ConnectorsWrapper,
    appCmdConnector: ApplicationCommandConnector,
    subscriptionFieldsService: SubscriptionFieldsService,
    deskproConnector: DeskproConnector,
    developerConnector: ThirdPartyDeveloperConnector,
    sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
    productionApplicationConnector: ThirdPartyApplicationProductionConnector,
    auditService: AuditService,
    val clock: Clock
  )(implicit val ec: ExecutionContext
  ) extends ClockNow {

  def createForUser(createApplicationRequest: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] =
    connectorWrapper.forEnvironment(createApplicationRequest.environment).thirdPartyApplicationConnector.create(createApplicationRequest)

  def update(updateApplicationRequest: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    connectorWrapper.forEnvironment(updateApplicationRequest.environment).thirdPartyApplicationConnector.update(updateApplicationRequest.id, updateApplicationRequest)

  def dispatchCmd(appId: ApplicationId, cmd: ApplicationCommand)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    appCmdConnector.dispatch(appId, cmd, Set.empty).map(_ => ApplicationUpdateSuccessful)
  }

  def updatePrivacyPolicyLocation(application: Application, userId: UserId, newLocation: PrivacyPolicyLocation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.ChangeProductionApplicationPrivacyPolicyLocation(userId, now(), newLocation)
    dispatchCmd(application.id, request)
  }

  def updateResponsibleIndividual(
      application: Application,
      userId: UserId,
      fullName: String,
      emailAddress: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.ChangeResponsibleIndividualToSelf(userId, now(), fullName, emailAddress)
    dispatchCmd(application.id, request)
  }

  def updateTermsConditionsLocation(
      application: Application,
      userId: UserId,
      newLocation: TermsAndConditionsLocation
    )(implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.ChangeProductionApplicationTermsAndConditionsLocation(userId, now(), newLocation)
    dispatchCmd(application.id, request)
  }

  def acceptResponsibleIndividualVerification(applicationId: ApplicationId, code: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.ChangeResponsibleIndividualToOther(code, now())
    dispatchCmd(applicationId, request)
  }

  def declineResponsibleIndividualVerification(applicationId: ApplicationId, code: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.DeclineResponsibleIndividual(code, now())
    dispatchCmd(applicationId, request)
  }

  def verifyResponsibleIndividual(
      application: Application,
      userId: UserId,
      requesterName: String,
      riName: String,
      riEmail: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.VerifyResponsibleIndividual(userId, now(), requesterName, riName, riEmail)
    dispatchCmd(application.id, request)
  }

  def fetchByApplicationId(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionData]] = {
    apmConnector.fetchApplicationById(applicationId)
  }

  def fetchCredentials(application: Application)(implicit hc: HeaderCarrier): Future[ApplicationToken] =
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.fetchCredentials(application.id)

  type ApiMap[V]   = Map[ApiContext, Map[ApiVersionNbr, V]]
  type FieldMap[V] = ApiMap[Map[FieldName, V]]

  def updateCheckInformation(application: Application, checkInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.updateApproval(application.id, checkInformation)
  }

  def requestUplift(applicationId: ApplicationId, applicationName: String, requestedBy: DeveloperSession)(implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful] = {
    for {
      result      <- connectorWrapper.productionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, requestedBy.email))
      upliftTicket = DeskproTicket.createForUplift(requestedBy.displayedName, requestedBy.email, applicationName, applicationId)
      _            = deskproConnector.createTicket(Some(requestedBy.developer.userId), upliftTicket)
    } yield result
  }

  def requestPrincipalApplicationDeletion(requester: DeveloperSession, application: Application)(implicit hc: HeaderCarrier): Future[TicketResult] = {

    val requesterName  = requester.displayedName
    val requesterEmail = requester.email
    val environment    = application.deployedTo
    val requesterRole  = roleForApplication(application, requesterEmail)
    val appId          = application.id

    if (environment.isSandbox || requesterRole.isAdministrator) {
      val deskproTicket = DeskproTicket.createForPrincipalApplicationDeletion(requesterName, requesterEmail, requesterRole, environment, application.name, appId)

      for {
        ticketResponse <- deskproConnector.createTicket(Some(requester.developer.userId), deskproTicket)
        _              <- auditService.audit(
                            ApplicationDeletionRequested,
                            Map(
                              "appId"                   -> appId.toString(),
                              "requestedByName"         -> requesterName,
                              "requestedByEmailAddress" -> requesterEmail.text,
                              "timestamp"               -> now().toString
                            )
                          )
      } yield ticketResponse
    } else {
      Future.failed(new ForbiddenException("Developer cannot request to delete a production application"))
    }
  }

  def deleteSubordinateApplication(requester: DeveloperSession, application: Application)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {

    val requesterEmail = requester.email
    val environment    = application.deployedTo
    val requesterRole  = roleForApplication(application, requesterEmail)
    val reasons        = "Subordinate application deleted by DevHub user"
    val instigator     = requester.developer.userId

    if (environment == Environment.SANDBOX && requesterRole == Collaborator.Roles.ADMINISTRATOR && application.access.accessType == AccessType.STANDARD) {

      val deleteRequest = ApplicationCommands.DeleteApplicationByCollaborator(instigator, reasons, now())
      dispatchCmd(application.id, deleteRequest)

    } else {
      Future.failed(new ForbiddenException("Only standard subordinate applications can be deleted by admins"))
    }
  }

  private def roleForApplication(application: Application, email: LaxEmailAddress) =
    application.role(email).getOrElse(throw new ApplicationNotFound)

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse] = {
    connectorWrapper.productionApplicationConnector.verify(verificationCode)
  }

  def requestDeveloperAccountDeletion(userId: UserId, name: String, email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    val deleteDeveloperTicket = DeskproTicket.deleteDeveloperAccount(name, email)

    for {
      ticketResponse <- deskproConnector.createTicket(Some(userId), deleteDeveloperTicket)
      _              <- auditService.audit(AccountDeletionRequested, Map("requestedByName" -> name, "requestedByEmailAddress" -> email.text, "timestamp" -> now().toString))
    } yield ticketResponse
  }

  def request2SVRemoval(userId: Option[UserId], name: String, email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    val remove2SVTicket = DeskproTicket.removeDeveloper2SV(name, email)

    for {
      ticketResponse <- deskproConnector.createTicket(userId, remove2SVTicket)
      _              <- auditService.audit(Remove2SVRequested, Map("requestedByEmailAddress" -> email.text, "timestamp" -> now().toString))
    } yield ticketResponse
  }

  def isApplicationNameValid(name: String, environment: Environment, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier): Future[ApplicationNameValidation] = {
    environment match {
      case Environment.PRODUCTION => connectorWrapper.productionApplicationConnector.validateName(name, selfApplicationId)
      case Environment.SANDBOX    => connectorWrapper.sandboxApplicationConnector.validateName(name, selfApplicationId)
    }
  }

  def userLogoutSurveyCompleted(email: LaxEmailAddress, name: String, rating: String, improvementSuggestions: String)(implicit hc: HeaderCarrier): Future[AuditResult] = {

    auditService.audit(
      UserLogoutSurveyCompleted,
      Map(
        "userEmailAddress"       -> email.text,
        "userName"               -> name,
        "satisfactionRating"     -> rating,
        "improvementSuggestions" -> improvementSuggestions,
        "timestamp"              -> now().toString
      )
    )
  }

  def requestProductonApplicationNameChange(
      userId: UserId,
      application: Application,
      newApplicationName: String,
      requesterName: String,
      requesterEmail: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ) = {

    def createDeskproTicket(application: Application, newApplicationName: String, requesterName: String, requesterEmail: LaxEmailAddress) = {
      val previousAppName = application.name
      val appId           = application.id

      DeskproTicket.createForRequestChangeOfProductionApplicationName(requesterName, requesterEmail, previousAppName, newApplicationName, appId)
    }

    val ticket = createDeskproTicket(application, newApplicationName, requesterName, requesterEmail)
    deskproConnector.createTicket(Some(userId), ticket)
  }

  def applicationConnectorFor(application: Application): ThirdPartyApplicationConnector = applicationConnectorFor(Some(application.deployedTo))

  def applicationConnectorFor(environment: Option[Environment]): ThirdPartyApplicationConnector =
    if (environment.contains(Environment.PRODUCTION)) {
      productionApplicationConnector
    } else {
      sandboxApplicationConnector
    }
}

object ApplicationService {

  val filterSubscriptionsForUplift: (Set[ApiIdentifier]) => (Map[ApplicationId, Set[ApiIdentifier]]) => Set[ApplicationId] =
    upliftableApiIdentifiers =>
      appSubscriptions =>
        appSubscriptions
          // All non-test non-example subscribed apis are upliftable AND at least one subscribed apis is present
          .view.mapValues(apis => apis.subsetOf(upliftableApiIdentifiers) && apis.nonEmpty).toMap
          .filter { case (_, isUpliftable) => isUpliftable }
          .keySet

  val isTestSupportOrExample: (List[ApiDefinition]) => (ApiIdentifier) => Boolean =
    apiDefinitions =>
      apiIdentifier =>
        apiDefinitions.find(_.context == apiIdentifier.context) match {
          case None      => false
          case Some(api) => api.isTestSupport || api.categories.contains(ApiCategory.EXAMPLE)
        }

  val filterSubscriptionsToRemoveTestAndExampleApis: List[ApiDefinition] => Map[ApplicationId, Set[ApiIdentifier]] => Map[ApplicationId, Set[ApiIdentifier]] =
    apiDefinitions =>
      subscriptionsByApplication => {
        subscriptionsByApplication.flatMap {
          case (id, subs) =>
            val filteredSubs = subs.filterNot(isTestSupportOrExample(apiDefinitions))
            if (filteredSubs.isEmpty) Map.empty[ApplicationId, Set[ApiIdentifier]] else Map(id -> filteredSubs)
        }
      }

  trait ApplicationConnector {
    def create(request: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse]
    def update(applicationId: ApplicationId, request: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
    def fetchByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationWithSubscriptionIds]]
    def fetchApplicationById(id: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[Application]]
    def fetchCredentials(id: ApplicationId)(implicit hc: HeaderCarrier): Future[ApplicationToken]
    def requestUplift(applicationId: ApplicationId, upliftRequest: UpliftRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful]
    def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse]
    def updateApproval(id: ApplicationId, approvalInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
    def validateName(name: String, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier): Future[ApplicationNameValidation]
    def unsubscribeFromApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
  }
}
