/*
 * Copyright 2020 HM Revenue & Customs
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

package service

import config.ApplicationConfig
import connectors._
import domain._
import domain.models.apidefinitions._
import domain.models.apidefinitions.APIStatus._
import domain.models.applications._
import domain.models.applications.Environment.{PRODUCTION, SANDBOX}
import domain.models.connectors.{AddTeamMemberRequest, AddTeamMemberResponse, DeskproTicket, TicketResult}
import domain.models.developers.DeveloperSession
import domain.models.subscriptions._
import domain.models.subscriptions.ApiSubscriptionFields._
import javax.inject.{Inject, Singleton}
import service.AuditAction.{AccountDeletionRequested, ApplicationDeletionRequested, Remove2SVRequested, UserLogoutSurveyCompleted}
import service.SubscriptionFieldsService.DefinitionsByApiVersion
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.{ExecutionContext, Future}
import domain.models.subscriptions.FieldName

@Singleton
class ApplicationService @Inject() (
    apmConnector: ApmConnector,
    connectorWrapper: ConnectorsWrapper,
    subscriptionFieldsService: SubscriptionFieldsService,
    deskproConnector: DeskproConnector,
    developerConnector: ThirdPartyDeveloperConnector,
    sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
    productionApplicationConnector: ThirdPartyApplicationProductionConnector,
    auditService: AuditService
)(implicit val ec: ExecutionContext) {

  def createForUser(createApplicationRequest: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] =
    connectorWrapper.forEnvironment(createApplicationRequest.environment).thirdPartyApplicationConnector.create(createApplicationRequest)

  def update(updateApplicationRequest: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    connectorWrapper.forEnvironment(updateApplicationRequest.environment).thirdPartyApplicationConnector.update(updateApplicationRequest.id, updateApplicationRequest)

  def fetchByApplicationId(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionData]] = {
    apmConnector.fetchApplicationById(applicationId)
  }

  def fetchCredentials(application: Application)(implicit hc: HeaderCarrier): Future[ApplicationToken] =
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.fetchCredentials(application.id)

  def xyz(application: ApplicationWithSubscriptionData)(implicit hc: HeaderCarrier): Future[Seq[APISubscriptionStatus]] = {
    for {
      subscribableApis <- apmConnector.fetchAllPossibleSubscriptions(application.application.id)
    } yield ???

    ???
  }

  def apisWithSubscriptions(application: Application)(implicit hc: HeaderCarrier): Future[Seq[APISubscriptionStatus]] = {

    def toApiSubscriptionStatuses(api: APISubscription, version: VersionSubscription, fieldDefinitions: DefinitionsByApiVersion): Future[APISubscriptionStatus] = {

      val apiIdentifier = ApiIdentifier(api.context, version.version.version)

      val subscriptionFieldsWithOutValues: Seq[SubscriptionFieldDefinition] =
        fieldDefinitions.getOrElse(apiIdentifier, Seq.empty)

      val subscriptionFieldsWithValues: Future[Seq[SubscriptionFieldValue]] =
        subscriptionFieldsService.fetchFieldsValues(application, subscriptionFieldsWithOutValues, apiIdentifier)

      subscriptionFieldsWithValues.map { fields: Seq[SubscriptionFieldValue] =>
        {
          val wrapper = SubscriptionFieldsWrapper(application.id, application.clientId, api.context, version.version.version, fields)
          APISubscriptionStatus(api.name, api.serviceName, api.context, version.version, version.subscribed, api.requiresTrust.getOrElse(false), wrapper, api.isTestSupport)
        }
      }
    }

    def toApiVersions(api: APISubscription, fieldDefinitions: DefinitionsByApiVersion): Seq[Future[APISubscriptionStatus]] = {

      api.versions
        .filterNot(_.version.status == RETIRED)
        .filterNot(s => s.version.status == DEPRECATED && !s.subscribed)
        .sortWith(APIDefinition.descendingVersion)
        .map(toApiSubscriptionStatuses(api, _, fieldDefinitions))
    }

    val thirdPartyAppConnector = connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector

    for {
      fieldDefinitions: DefinitionsByApiVersion <- subscriptionFieldsService.getAllFieldDefinitions(application.deployedTo)
      subscriptions: Seq[APISubscription] <- thirdPartyAppConnector.fetchSubscriptions(application.id)
      apiVersions <- Future.sequence(subscriptions.flatMap(toApiVersions(_, fieldDefinitions)))
    } yield apiVersions
  }

  def subscribeToApi(application: Application, context: ApiContext, version: ApiVersion)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val connectors = connectorWrapper.forEnvironment(application.deployedTo)

    val apiIdentifier = ApiIdentifier(context, version)

    trait HasSucceeded
    object HasSucceeded extends HasSucceeded

    def ensureEmptyValuesWhenNoneExists(fieldDefinitions: Seq[SubscriptionFieldDefinition]): Future[HasSucceeded] = {

      for {
        oldValues <- subscriptionFieldsService.fetchFieldsValues(application, fieldDefinitions, apiIdentifier)
        saveResponse <- subscriptionFieldsService.saveBlankFieldValues(application, context, version, oldValues)
      } yield saveResponse match {
        case SaveSubscriptionFieldsSuccessResponse => HasSucceeded
        case error =>
          val errorMessage = s"Failed to save blank subscription field values: $error"
          throw new RuntimeException(errorMessage)
      }
    }

    def ensureSavedValuesForAnyDefinitions(defns: Seq[SubscriptionFieldDefinition]): Future[HasSucceeded] = {
      if (defns.nonEmpty) {
        ensureEmptyValuesWhenNoneExists(defns)
      } else {
        Future.successful(HasSucceeded)
      }
    }

    val subscribeResponse: Future[ApplicationUpdateSuccessful] = connectors.thirdPartyApplicationConnector.subscribeToApi(application.id, apiIdentifier)
    val fieldDefinitions: Future[Seq[SubscriptionFieldDefinition]] = subscriptionFieldsService.getFieldDefinitions(application, apiIdentifier)

    fieldDefinitions
      .flatMap(ensureSavedValuesForAnyDefinitions)
      .flatMap(_ => subscribeResponse)
  }

  def unsubscribeFromApi(application: Application, context: ApiContext, version: ApiVersion)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val connectors = connectorWrapper.forEnvironment(application.deployedTo)
    connectors.thirdPartyApplicationConnector.unsubscribeFromApi(application.id, context, version)
  }

  def isSubscribedToApi(application: Application, apiName: String, apiContext: ApiContext, apiVersion: ApiVersion)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val thirdPartyAppConnector = connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector

    for {
      subscriptions <- thirdPartyAppConnector.fetchSubscriptions(application.id)
      subscription = subscriptions
        .find(sub => sub.name == apiName && sub.context == apiContext && sub.versions.exists(v => v.version.version == apiVersion && v.subscribed))
    } yield subscription.isDefined
  }

  def fetchAllSubscriptions(application: Application)(implicit hc: HeaderCarrier): Future[Seq[APISubscription]] = {
    val thirdPartyAppConnector = connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector

    for {
      subscriptions <- thirdPartyAppConnector.fetchSubscriptions(application.id)
      subscription = subscriptions
        .filter(sub => sub.versions.exists(v => v.subscribed))
    } yield subscription
  }

  def addClientSecret(application: Application, actorEmailAddress: String)(implicit hc: HeaderCarrier): Future[(String, String)] = {
    connectorWrapper.forEnvironment(application.deployedTo).thirdPartyApplicationConnector.addClientSecrets(application.id, ClientSecretRequest(actorEmailAddress))
  }

  def deleteClientSecret(application: Application, clientSecretId: String, actorEmailAddress: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    connectorWrapper
      .forEnvironment(application.deployedTo)
      .thirdPartyApplicationConnector
      .deleteClientSecret(application.id, clientSecretId, actorEmailAddress)

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
            "timestamp" -> DateTimeUtils.now.toString
          )
        )
      } yield ticketResponse
    } else {
      Future.failed(new ForbiddenException("Developer cannot request to delete a production application"))
    }
  }

  def deleteSubordinateApplication(requester: DeveloperSession, application: Application)(implicit hc: HeaderCarrier): Future[Unit] = {

    val requesterEmail = requester.email
    val environment = application.deployedTo
    val requesterRole = roleForApplication(application, requesterEmail)

    if (environment == Environment.SANDBOX && requesterRole == Role.ADMINISTRATOR && application.access.accessType == AccessType.STANDARD) {

      applicationConnectorFor(application).deleteApplication(application.id)

    } else {
      Future.failed(new ForbiddenException("Only standard subordinate applications can be deleted by admins"))
    }
  }

  private def roleForApplication(application: Application, email: String) =
    application.role(email).getOrElse(throw new ApplicationNotFound)

  def applicationConnectorFor(application: Application): ThirdPartyApplicationConnector =
    if (application.deployedTo == PRODUCTION) productionApplicationConnector else sandboxApplicationConnector

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse] = {
    connectorWrapper.productionApplicationConnector.verify(verificationCode)
  }

  def addTeamMember(app: Application, requestingEmail: String, teamMember: Collaborator)(implicit hc: HeaderCarrier): Future[AddTeamMemberResponse] = {

    val otherAdminEmails = app.collaborators
      .filter(_.role.isAdministrator)
      .map(_.emailAddress)
      .filterNot(_ == requestingEmail)

    for {
      otherAdmins <- developerConnector.fetchByEmails(otherAdminEmails)
      adminsToEmail = otherAdmins.filter(_.verified.contains(true)).map(_.email)
      developer <- developerConnector.fetchDeveloper(teamMember.emailAddress)
      _ <- if (developer.isEmpty) developerConnector.createUnregisteredUser(teamMember.emailAddress) else Future.successful(())
      request = domain.models.connectors.AddTeamMemberRequest(requestingEmail, teamMember, developer.isDefined, adminsToEmail.toSet)
      connector = connectorWrapper.forEnvironment(app.deployedTo)
      appConnector = connector.thirdPartyApplicationConnector
      response <- appConnector.addTeamMember(app.id, request)
    } yield response
  }

  def removeTeamMember(app: Application, teamMemberToRemove: String, requestingEmail: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val otherAdminEmails = app.collaborators
      .filter(_.role.isAdministrator)
      .map(_.emailAddress)
      .filterNot(_ == requestingEmail)
      .filterNot(_ == teamMemberToRemove)

    for {
      otherAdmins <- developerConnector.fetchByEmails(otherAdminEmails)
      adminsToEmail = otherAdmins.filter(_.verified.contains(true)).map(_.email)
      connectors = connectorWrapper.forEnvironment(app.deployedTo)
      response <- connectors.thirdPartyApplicationConnector.removeTeamMember(app.id, teamMemberToRemove, requestingEmail, adminsToEmail)
    } yield response
  }

  def fetchByTeamMemberEmail(email: String)(implicit hc: HeaderCarrier): Future[Seq[Application]] = {
    def fetchProductionApplications = connectorWrapper.productionApplicationConnector.fetchByTeamMemberEmail(email)

    def fetchSandboxApplications: Future[Seq[Application]] = {
      connectorWrapper.sandboxApplicationConnector.fetchByTeamMemberEmail(email) recover {
        case _ => Seq.empty
      }
    }

    val productionApplicationsFuture = fetchProductionApplications
    val sandboxApplicationsFuture = fetchSandboxApplications

    for {
      productionApplications <- productionApplicationsFuture
      sandboxApplications <- sandboxApplicationsFuture
    } yield (productionApplications ++ sandboxApplications).sorted
  }

  def requestDeveloperAccountDeletion(name: String, email: String)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    val deleteDeveloperTicket = DeskproTicket.deleteDeveloperAccount(name, email)

    for {
      ticketResponse <- deskproConnector.createTicket(deleteDeveloperTicket)
      _ <- auditService.audit(AccountDeletionRequested, Map("requestedByName" -> name, "requestedByEmailAddress" -> email, "timestamp" -> DateTimeUtils.now.toString))
    } yield ticketResponse
  }

  def request2SVRemoval(email: String)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    val remove2SVTicket = DeskproTicket.removeDeveloper2SV(email)

    for {
      ticketResponse <- deskproConnector.createTicket(remove2SVTicket)
      _ <- auditService.audit(Remove2SVRequested, Map("requestedByEmailAddress" -> email, "timestamp" -> DateTimeUtils.now.toString))
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
        "timestamp" -> DateTimeUtils.now.toString
      )
    )
  }

  def applicationConnectorFor(environment: Option[Environment]): ThirdPartyApplicationConnector =
    if (environment.contains(PRODUCTION)) {
      productionApplicationConnector
    } else {
      sandboxApplicationConnector
    }
}

object ApplicationService {
  trait ApplicationConnector {
    def create(request: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse]
    def update(applicationId: ApplicationId, request: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
    def fetchByTeamMemberEmail(email: String)(implicit hc: HeaderCarrier): Future[Seq[Application]]
    def addTeamMember(applicationId: ApplicationId, teamMember: AddTeamMemberRequest)(implicit hc: HeaderCarrier): Future[AddTeamMemberResponse]
    def removeTeamMember(applicationId: ApplicationId, teamMemberToDelete: String, requestingEmail: String, adminsToEmail: Seq[String])(
        implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful]
    def fetchApplicationById(id: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[Application]]
    def fetchSubscriptions(id: ApplicationId)(implicit hc: HeaderCarrier): Future[Seq[APISubscription]]
    def subscribeToApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
    def unsubscribeFromApi(applicationId: ApplicationId, context: ApiContext, version: ApiVersion)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
    def fetchCredentials(id: ApplicationId)(implicit hc: HeaderCarrier): Future[ApplicationToken]
    def requestUplift(applicationId: ApplicationId, upliftRequest: UpliftRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful]
    def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse]
    def updateApproval(id: ApplicationId, approvalInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
    def addClientSecrets(id: ApplicationId, clientSecretRequest: ClientSecretRequest)(implicit hc: HeaderCarrier): Future[(String, String)]
    def deleteClientSecret(applicationId: ApplicationId, clientSecretId: String, actorEmailAddress: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
    def validateName(name: String, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier): Future[ApplicationNameValidation]
    def deleteApplication(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Unit]
  }
}
