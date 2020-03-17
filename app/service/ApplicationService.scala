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
import domain.APIStatus._
import domain.ApiSubscriptionFields.{FieldDefinitions, SubscriptionField, SubscriptionFieldsWrapper}
import domain.Environment.{PRODUCTION, SANDBOX}
import javax.inject.{Inject, Singleton}
import service.AuditAction.{AccountDeletionRequested, ApplicationDeletionRequested, Remove2SVRequested, UserLogoutSurveyCompleted}
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationService @Inject()(connectorWrapper: ConnectorsWrapper,
                                   subscriptionFieldsService: SubscriptionFieldsService,
                                   deskproConnector: DeskproConnector,
                                   applicationConfig: ApplicationConfig,
                                   developerConnector: ThirdPartyDeveloperConnector,
                                   sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
                                   productionApplicationConnector: ThirdPartyApplicationProductionConnector,
                                   auditService: AuditService)(implicit val ec: ExecutionContext) {

  def createForUser(createApplicationRequest: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] =
    connectorWrapper.connectorsForEnvironment(createApplicationRequest.environment).thirdPartyApplicationConnector.create(createApplicationRequest)

  def update(updateApplicationRequest: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    connectorWrapper.connectorsForEnvironment(updateApplicationRequest.environment)
      .thirdPartyApplicationConnector.update(updateApplicationRequest.id, updateApplicationRequest)

  def fetchByApplicationId(id: String)(implicit hc: HeaderCarrier): Future[Application] = {
    connectorWrapper.fetchApplicationById(id)
  }

  def fetchCredentials(id: String)(implicit hc: HeaderCarrier): Future[ApplicationToken] =
    connectorWrapper.forApplication(id).flatMap(_.thirdPartyApplicationConnector.fetchCredentials(id))

  def apisWithSubscriptions(application: Application)(implicit hc: HeaderCarrier): Future[Seq[APISubscriptionStatus]] = {

    def toApiSubscriptionStatuses(api: APISubscription,
                                  version: VersionSubscription,
                                  fieldDefinitions: Map[APIIdentifier, FieldDefinitions]): Future[APISubscriptionStatus] = {
      val apiIdentifier = APIIdentifier(api.context, version.version.version)

      val subscriptionFieldsWithOutValues: Seq[SubscriptionField] =
        fieldDefinitions
          .get(apiIdentifier)
          .map((fieldDefinitions: FieldDefinitions) => fieldDefinitions.fieldDefinitions)
          .getOrElse(Seq.empty)

      val subscriptionFieldsWithValues: Future[Seq[SubscriptionField]] = subscriptionFieldsService.fetchFieldsValues(application, subscriptionFieldsWithOutValues, apiIdentifier)

      subscriptionFieldsWithValues.map { fields: Seq[SubscriptionField] =>
        APISubscriptionStatus(
          api.name,
          api.serviceName,
          api.context,
          version.version,
          version.subscribed,
          api.requiresTrust.getOrElse(false),
          Some(SubscriptionFieldsWrapper(application.id, application.clientId, api.context, version.version.version, fields)),
          api.isTestSupport)
      }
    }

    def toApiVersions(api: APISubscription,
                      fieldDefinitions: Map[APIIdentifier, FieldDefinitions]): Seq[Future[APISubscriptionStatus]] = {

      api.versions
        .filterNot(_.version.status == RETIRED)
        .filterNot(s => s.version.status == DEPRECATED && !s.subscribed)
        .sortWith(APIDefinition.descendingVersion)
        .map(toApiSubscriptionStatuses(api, _, fieldDefinitions))
    }

    val thirdPartyAppConnector = connectorWrapper.connectorsForEnvironment(application.deployedTo).thirdPartyApplicationConnector

    for {
      fieldDefinitions <- subscriptionFieldsService.getAllFieldDefinitions(application.deployedTo)
      subscriptions <- thirdPartyAppConnector.fetchSubscriptions(application.id)
      apiVersions <- Future.sequence(subscriptions.flatMap(toApiVersions(_, fieldDefinitions)))
    } yield apiVersions
  }

  def subscribeToApi(application: Application, context: String, version: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val connectors = connectorWrapper.connectorsForEnvironment(application.deployedTo)

    val apiIdentifier = APIIdentifier(context, version)

    def createEmptyFieldValues(fieldDefinitions: Seq[SubscriptionField]) = {
      fieldDefinitions
        .map(d => (d.name, ""))
        .toMap
    }

    def ifNoSubscriptionValuesSaveEmptyValues(fieldDefinitions: Seq[SubscriptionField]) = {
      subscriptionFieldsService
        .fetchFieldsValues(application, fieldDefinitions, apiIdentifier)
        .map(fieldDefinitionValues => {
          if(!fieldDefinitionValues.exists(field => field.value.isDefined)) {
            subscriptionFieldsService.saveFieldValues(application.id, context, version, createEmptyFieldValues(fieldDefinitions))
          }
        })
    }

    for {
      subscribeResponse <- connectors.thirdPartyApplicationConnector.subscribeToApi(application.id, apiIdentifier)
      fieldDefinitions <- subscriptionFieldsService.getFieldDefinitions(application, apiIdentifier)
    } yield {
      if (fieldDefinitions.nonEmpty){
        ifNoSubscriptionValuesSaveEmptyValues(fieldDefinitions)
      }

      subscribeResponse
    }
  }

  def unsubscribeFromApi(application: Application, context: String, version: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val connectors = connectorWrapper.connectorsForEnvironment(application.deployedTo)

    for {
      unsubscribeResult <- connectors.thirdPartyApplicationConnector.unsubscribeFromApi(application.id, context, version)
      _ <- connectors.apiSubscriptionFieldsConnector.deleteFieldValues(application.id, context, version)
    } yield {
      unsubscribeResult
    }
  }

  def isSubscribedToApi(application: Application, apiName: String, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val thirdPartyAppConnector = connectorWrapper.connectorsForEnvironment(application.deployedTo).thirdPartyApplicationConnector

    for {
      subscriptions <- thirdPartyAppConnector.fetchSubscriptions(application.id)
      subscription = subscriptions
        .find(sub => sub.name == apiName && sub.context == apiContext && sub.versions.exists(v => v.version.version == apiVersion && v.subscribed))
    } yield subscription.isDefined
  }

  def fetchAllSubscriptions(application: Application)(implicit hc: HeaderCarrier): Future[Seq[APISubscription]] = {
    val thirdPartyAppConnector = connectorWrapper.connectorsForEnvironment(application.deployedTo).thirdPartyApplicationConnector

    for {
      subscriptions <- thirdPartyAppConnector.fetchSubscriptions(application.id)
      subscription = subscriptions
        .filter(sub => sub.versions.exists(v => v.subscribed))
    } yield subscription
  }

  def addClientSecret(id: String, actorEmailAddress: String)(implicit hc: HeaderCarrier): Future[ApplicationToken] = {
    connectorWrapper.forApplication(id).flatMap(_.thirdPartyApplicationConnector.addClientSecrets(id, ClientSecretRequest(actorEmailAddress)))
  }

  def deleteClientSecrets(appId: String, actorEmailAddress: String, clientSecrets: Seq[String])
                         (implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    connectorWrapper.forApplication(appId)
      .flatMap(_.thirdPartyApplicationConnector.deleteClientSecrets(appId, DeleteClientSecretsRequest(actorEmailAddress, clientSecrets)))
  }

  def updateCheckInformation(id: String, checkInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    connectorWrapper.forApplication(id).flatMap(_.thirdPartyApplicationConnector.updateApproval(id, checkInformation))
  }

  def requestUplift(applicationId: String, applicationName: String, requestedBy: DeveloperSession)
                   (implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful] = {
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
      val deskproTicket = DeskproTicket.createForPrincipalApplicationDeletion(
        requesterName,
        requesterEmail,
        requesterRole,
        environment,
        application.name,
        appId)

      for {
        ticketResponse <- deskproConnector.createTicket(deskproTicket)
        _ <- auditService.audit(ApplicationDeletionRequested, Map(
          "appId" -> appId,
          "requestedByName" -> requesterName,
          "requestedByEmailAddress" -> requesterEmail,
          "timestamp" -> DateTimeUtils.now.toString
        ))
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

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationSuccessful] = {
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
      request = AddTeamMemberRequest(requestingEmail, teamMember, developer.isDefined, adminsToEmail.toSet)
      connector <- connectorWrapper.forApplication(app.id)
      appConnector = connector.thirdPartyApplicationConnector
      response <- appConnector.addTeamMember(app.id, request)
    } yield response
  }

  def removeTeamMember(app: Application,
                       teamMemberToRemove: String,
                       requestingEmail: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {

    val otherAdminEmails = app.collaborators
      .filter(_.role.isAdministrator)
      .map(_.emailAddress)
      .filterNot(_ == requestingEmail)
      .filterNot(_ == teamMemberToRemove)

    for {
      otherAdmins <- developerConnector.fetchByEmails(otherAdminEmails)
      adminsToEmail = otherAdmins.filter(_.verified.contains(true)).map(_.email)
      connectors <- connectorWrapper.forApplication(app.id)
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

  def isApplicationNameValid(name: String, environment: Environment, selfApplicationId: Option[String])
                            (implicit hc: HeaderCarrier): Future[ApplicationNameValidation] = {
    environment match {
      case PRODUCTION => connectorWrapper.productionApplicationConnector.validateName(name, selfApplicationId)
      case SANDBOX => connectorWrapper.sandboxApplicationConnector.validateName(name, selfApplicationId)
    }
  }

  def userLogoutSurveyCompleted(email: String, name: String, rating: String, improvementSuggestions: String)
                               (implicit hc: HeaderCarrier): Future[AuditResult] = {

    auditService.audit(UserLogoutSurveyCompleted, Map(
      "userEmailAddress" -> email,
      "userName" -> name,
      "satisfactionRating" -> rating,
      "improvementSuggestions" -> improvementSuggestions,
      "timestamp" -> DateTimeUtils.now.toString))
  }

  def applicationConnectorFor(environment: Option[Environment]): ThirdPartyApplicationConnector =
    if (environment.contains(PRODUCTION))
      productionApplicationConnector
    else
      sandboxApplicationConnector
}
