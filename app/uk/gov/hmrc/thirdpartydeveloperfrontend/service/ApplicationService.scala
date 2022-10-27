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

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment.{PRODUCTION, SANDBOX}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketResult}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions._

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction.{AccountDeletionRequested, ApplicationDeletionRequested, Remove2SVRequested, UserLogoutSurveyCompleted}
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.AddTeamMemberRequest

import java.time.{Clock, LocalDateTime}

@Singleton
class ApplicationService @Inject() (
    apmConnector: ApmConnector,
    connectorWrapper: ConnectorsWrapper,
    subscriptionFieldsService: SubscriptionFieldsService,
    subscriptionService: SubscriptionsService,
    deskproConnector: DeskproConnector,
    developerConnector: ThirdPartyDeveloperConnector,
    sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
    productionApplicationConnector: ThirdPartyApplicationProductionConnector,
    auditService: AuditService,
    clock: Clock
)(implicit val ec: ExecutionContext) {

  def createForUser(createApplicationRequest: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] =
    connectorWrapper.forEnvironment(createApplicationRequest.environment).thirdPartyApplicationConnector.create(createApplicationRequest)

  def update(updateApplicationRequest: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    connectorWrapper.forEnvironment(updateApplicationRequest.environment).thirdPartyApplicationConnector.update(updateApplicationRequest.id, updateApplicationRequest)

  def updatePrivacyPolicyLocation(application: Application, userId: UserId, newLocation: PrivacyPolicyLocation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = ChangeProductionApplicationPrivacyPolicyLocation(userId,  LocalDateTime.now(clock), newLocation)
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.applicationUpdate(application.id, request)
  }

  def updateResponsibleIndividual(application: Application, userId: UserId, fullName: String, emailAddress: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = ChangeResponsibleIndividualToSelf(userId,  LocalDateTime.now(clock), fullName, emailAddress)
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.applicationUpdate(application.id, request)
  }

  def updateTermsConditionsLocation(application: Application, userId: UserId, newLocation: TermsAndConditionsLocation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = ChangeProductionApplicationTermsAndConditionsLocation(userId,  LocalDateTime.now(clock), newLocation)
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.applicationUpdate(application.id, request)
  }

  def acceptResponsibleIndividualVerification(applicationId: ApplicationId, code: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = ChangeResponsibleIndividualToOther(code, LocalDateTime.now(clock))
    connectorWrapper.productionApplicationConnector.applicationUpdate(applicationId, request)
  }

  def declineResponsibleIndividualVerification(applicationId: ApplicationId, code: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = DeclineResponsibleIndividual(code, LocalDateTime.now(clock))
    connectorWrapper.productionApplicationConnector.applicationUpdate(applicationId, request)
  }

  def verifyResponsibleIndividual(application: Application, userId: UserId, requesterName: String, riName: String, riEmail: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = VerifyResponsibleIndividual(userId,  LocalDateTime.now(clock), requesterName, riName, riEmail)
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.applicationUpdate(application.id, request)
  }

  def fetchByApplicationId(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionData]] = {
    apmConnector.fetchApplicationById(applicationId)
  }

  def fetchCredentials(application: Application)(implicit hc: HeaderCarrier): Future[ApplicationToken] =
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.fetchCredentials(application.id)

  type ApiMap[V] = Map[ApiContext, Map[ApiVersion, V]]
  type FieldMap[V] = ApiMap[Map[FieldName,V]]

  def subscribeToApi(application: Application, actor: CollaboratorActor, apiIdentifier: ApiIdentifier)
                    (implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = SubscribeToApi(actor, apiIdentifier, LocalDateTime.now(clock))
    subscriptionService.subscribeToApi(application.id, request)
  }

  def unsubscribeFromApi(application: Application, actor: CollaboratorActor, apiIdentifier: ApiIdentifier)
                        (implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = UnsubscribeFromApi(actor, apiIdentifier, LocalDateTime.now(clock))
    subscriptionService.unsubscribeFromApi(application.id, request)
  }

  def isSubscribedToApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[Boolean] = {
    subscriptionService.isSubscribedToApi(applicationId,apiIdentifier)
  }

  def addClientSecret(application: Application, actor: CollaboratorActor)(implicit hc: HeaderCarrier): Future[(String, String)] = {
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.addClientSecrets(application.id, ClientSecretRequest(actor, LocalDateTime.now(clock)))
  }

  def deleteClientSecret(application: Application, actor: CollaboratorActor, clientSecretId: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    connectorWrapper
      .forEnvironment(application.deployedTo)
      .thirdPartyApplicationConnector
      .applicationUpdate(application.id, RemoveClientSecret(actor, clientSecretId, LocalDateTime.now(clock)))

  def updateCheckInformation(application: Application, checkInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.updateApproval(application.id, checkInformation)
  }

  def requestUplift(applicationId: ApplicationId, applicationName: String, requestedBy: DeveloperSession)(implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful] = {
    for {
      result <- connectorWrapper.productionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, requestedBy.email))
      upliftTicket = DeskproTicket.createForUplift(requestedBy.displayedName, requestedBy.email, applicationName, applicationId)
      _ = deskproConnector.createTicket(upliftTicket)
    } yield result
  }

  def requestPrincipalApplicationDeletion(requester: DeveloperSession, application: Application)(implicit hc: HeaderCarrier): Future[TicketResult] = {

    val requesterName = requester.displayedName
    val requesterEmail = requester.email
    val environment = application.deployedTo
    val requesterRole = roleForApplication(application, requesterEmail)
    val appId = application.id

    if (environment.isSandbox || requesterRole.isAdministrator) {
      val deskproTicket = DeskproTicket.createForPrincipalApplicationDeletion(requesterName, requesterEmail, requesterRole, environment, application.name, appId)

      for {
        ticketResponse <- deskproConnector.createTicket(deskproTicket)
        _ <- auditService.audit(
          ApplicationDeletionRequested,
          Map(
            "appId" -> appId.value,
            "requestedByName" -> requesterName,
            "requestedByEmailAddress" -> requesterEmail,
            "timestamp" -> LocalDateTime.now(clock).toString
          )
        )
      } yield ticketResponse
    } else {
      Future.failed(new ForbiddenException("Developer cannot request to delete a production application"))
    }
  }

  def deleteSubordinateApplication(requester: DeveloperSession, application: Application)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {

    val requesterEmail = requester.email
    val environment = application.deployedTo
    val requesterRole = roleForApplication(application, requesterEmail)
    val reasons = "Subordinate application deleted by DevHub user"
    val instigator = requester.developer.userId

    if (environment == Environment.SANDBOX && requesterRole == CollaboratorRole.ADMINISTRATOR && application.access.accessType == AccessType.STANDARD) {

      val deleteRequest = DeleteApplicationByCollaborator(instigator, reasons, LocalDateTime.now(clock))
      applicationConnectorFor(application).applicationUpdate(application.id, deleteRequest)

    } else {
      Future.failed(new ForbiddenException("Only standard subordinate applications can be deleted by admins"))
    }
  }

  private def roleForApplication(application: Application, email: String) =
    application.role(email).getOrElse(throw new ApplicationNotFound)


  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse] = {
    connectorWrapper.productionApplicationConnector.verify(verificationCode)
  }

  def addTeamMember(app: Application, requestingEmail: String, teamMember: AddCollaborator)(implicit hc: HeaderCarrier): Future[Unit] = {
    val request = AddTeamMemberRequest(teamMember.emailAddress, teamMember.role, Some(requestingEmail))
    apmConnector.addTeamMember(app.id, request)
  }

  def removeTeamMember(app: Application, teamMemberToRemove: String, requestingEmail: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val otherAdminEmails = app.collaborators
      .filter(_.role.isAdministrator)
      .map(_.emailAddress)
      .filterNot(_ == requestingEmail)
      .filterNot(_ == teamMemberToRemove)

    for {
      otherAdmins <- developerConnector.fetchByEmails(otherAdminEmails)
      adminsToEmail = otherAdmins.filter(_.verified.contains(true)).map(_.email).toSet
      connectors = connectorWrapper.forEnvironment(app.deployedTo)
      response <- connectors.thirdPartyApplicationConnector.removeTeamMember(app.id, teamMemberToRemove, requestingEmail, adminsToEmail)
    } yield response
  }

  def requestDeveloperAccountDeletion(name: String, email: String)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    val deleteDeveloperTicket = DeskproTicket.deleteDeveloperAccount(name, email)

    for {
      ticketResponse <- deskproConnector.createTicket(deleteDeveloperTicket)
      _ <- auditService.audit(AccountDeletionRequested, Map("requestedByName" -> name, "requestedByEmailAddress" -> email, "timestamp" -> LocalDateTime.now(clock).toString))
    } yield ticketResponse
  }

  def request2SVRemoval(name: String, email: String)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    val remove2SVTicket = DeskproTicket.removeDeveloper2SV(name, email)

    for {
      ticketResponse <- deskproConnector.createTicket(remove2SVTicket)
      _ <- auditService.audit(Remove2SVRequested, Map("requestedByEmailAddress" -> email, "timestamp" -> LocalDateTime.now(clock).toString))
    } yield ticketResponse
  }

  def isApplicationNameValid(name: String, environment: Environment, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier): Future[ApplicationNameValidation] = {
    environment match {
      case PRODUCTION => connectorWrapper.productionApplicationConnector.validateName(name, selfApplicationId)
      case SANDBOX    => connectorWrapper.sandboxApplicationConnector.validateName(name, selfApplicationId)
    }
  }

  def userLogoutSurveyCompleted(email: String, name: String, rating: String, improvementSuggestions: String)(implicit hc: HeaderCarrier): Future[AuditResult] = {

    auditService.audit(
      UserLogoutSurveyCompleted,
      Map(
        "userEmailAddress" -> email,
        "userName" -> name,
        "satisfactionRating" -> rating,
        "improvementSuggestions" -> improvementSuggestions,
        "timestamp" -> LocalDateTime.now(clock).toString
      )
    )
  }

  def requestProductonApplicationNameChange(application: Application, newApplicationName: String, requesterName: String, requesterEmail: String)(implicit hc: HeaderCarrier) = {

    def createDeskproTicket(application: Application, newApplicationName: String, requesterName: String, requesterEmail: String) = {
      val previousAppName = application.name
      val appId = application.id

      DeskproTicket.createForRequestChangeOfProductionApplicationName(requesterName, requesterEmail, previousAppName, newApplicationName, appId)
    }

    val ticket = createDeskproTicket(application, newApplicationName, requesterName, requesterEmail)
    deskproConnector.createTicket(ticket)
  }

  def applicationConnectorFor(application: Application): ThirdPartyApplicationConnector = applicationConnectorFor(Some(application.deployedTo))

  def applicationConnectorFor(environment: Option[Environment]): ThirdPartyApplicationConnector =
    if (environment.contains(PRODUCTION)) {
      productionApplicationConnector
    } else {
      sandboxApplicationConnector
    }
}

object ApplicationService {
  val filterSubscriptionsForUplift: (Set[ApiIdentifier]) => (Map[ApplicationId, Set[ApiIdentifier]]) => Set[ApplicationId] = 
    (upliftableApiIdentifiers) => (appSubscriptions) =>
      appSubscriptions
      // All non-test non-example subscribed apis are upliftable AND at least one subscribed apis is present
      .mapValues(apis => apis.subsetOf(upliftableApiIdentifiers) && apis.nonEmpty)
      .filter{ case (_, isUpliftable) => isUpliftable}
      .keySet

  val isTestSupportOrExample: (Map[ApiContext, ApiData]) => (ApiIdentifier) => Boolean = 
    (apis) => (id) => apis.get(id.context) match {
      case None => false
      case Some(api) => api.isTestSupport || api.categories.contains(ApiCategory.EXAMPLE)
    }

  val filterSubscriptionsToRemoveTestAndExampleApis: (Map[ApiContext, ApiData]) => (Map[ApplicationId, Set[ApiIdentifier]]) => Map[ApplicationId, Set[ApiIdentifier]] =
    (apis) => (subscriptionsByApplication) => {
      subscriptionsByApplication.flatMap { 
        case (id, subs) =>
          val filteredSubs = subs.filterNot(isTestSupportOrExample(apis))
          if(filteredSubs.isEmpty) Map.empty[ApplicationId, Set[ApiIdentifier]] else Map(id -> filteredSubs)
      }
    }

  trait ApplicationConnector {
    def create(request: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse]
    def update(applicationId: ApplicationId, request: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
    def fetchByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationWithSubscriptionIds]]
    def removeTeamMember(applicationId: ApplicationId, teamMemberToDelete: String, requestingEmail: String, adminsToEmail: Set[String])(
        implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful]
    def fetchApplicationById(id: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[Application]]
    def fetchCredentials(id: ApplicationId)(implicit hc: HeaderCarrier): Future[ApplicationToken]
    def requestUplift(applicationId: ApplicationId, upliftRequest: UpliftRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful]
    def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse]
    def updateApproval(id: ApplicationId, approvalInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
    def addClientSecrets(id: ApplicationId, clientSecretRequest: ClientSecretRequest)(implicit hc: HeaderCarrier): Future[(String, String)]
    def validateName(name: String, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier): Future[ApplicationNameValidation]
    def applicationUpdate(applicationId: ApplicationId, request: ApplicationUpdate)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
  }
}
