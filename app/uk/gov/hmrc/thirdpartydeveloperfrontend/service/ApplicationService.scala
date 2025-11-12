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

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ApiDefinition}
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.AccessType
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{PrivacyPolicyLocation, TermsAndConditionsLocation}
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommand, ApplicationCommands}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CreateTicketRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction.{AccountDeletionRequested, ApplicationDeletionRequested, Remove2SVRequested}

@Singleton
class ApplicationService @Inject() (
    apmApplicationConnector: ApmConnectorApplicationModule,
    connectorWrapper: ConnectorsWrapper,
    apmCmdModule: ApmConnectorCommandModule,
    subscriptionFieldsService: SubscriptionFieldsService,
    apiPlatformDeskproConnector: ApiPlatformDeskproConnector,
    developerConnector: ThirdPartyDeveloperConnector,
    thirdPartyOrchestratorConnector: ThirdPartyOrchestratorConnector,
    auditService: AuditService,
    val clock: Clock
  )(implicit val ec: ExecutionContext
  ) extends ClockNow {

  def createForUser(createApplicationRequest: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] =
    thirdPartyOrchestratorConnector.create(createApplicationRequest)

  def dispatchCmd(appId: ApplicationId, cmd: ApplicationCommand)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    apmCmdModule.dispatch(appId, cmd, Set.empty).map(_ => ApplicationUpdateSuccessful)
  }

  def updatePrivacyPolicyLocation(application: ApplicationWithCollaborators, userId: UserId, newLocation: PrivacyPolicyLocation)(implicit hc: HeaderCarrier)
      : Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.ChangeProductionApplicationPrivacyPolicyLocation(userId, instant(), newLocation)
    dispatchCmd(application.id, request)
  }

  def updateResponsibleIndividual(
      application: ApplicationWithCollaborators,
      userId: UserId,
      fullName: String,
      emailAddress: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.ChangeResponsibleIndividualToSelf(userId, instant(), fullName, emailAddress)
    dispatchCmd(application.id, request)
  }

  def updateTermsConditionsLocation(
      application: ApplicationWithCollaborators,
      userId: UserId,
      newLocation: TermsAndConditionsLocation
    )(implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.ChangeProductionApplicationTermsAndConditionsLocation(userId, instant(), newLocation)
    dispatchCmd(application.id, request)
  }

  def acceptResponsibleIndividualVerification(applicationId: ApplicationId, code: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.ChangeResponsibleIndividualToOther(code, instant())
    dispatchCmd(applicationId, request)
  }

  def declineResponsibleIndividualVerification(applicationId: ApplicationId, code: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.DeclineResponsibleIndividual(code, instant())
    dispatchCmd(applicationId, request)
  }

  def verifyResponsibleIndividual(
      application: ApplicationWithCollaborators,
      userId: UserId,
      requesterName: String,
      riName: String,
      riEmail: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful] = {
    val request = ApplicationCommands.VerifyResponsibleIndividual(userId, instant(), requesterName, riName, riEmail)
    dispatchCmd(application.id, request)
  }

  def fetchByApplicationId(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionFields]] = {
    apmApplicationConnector.fetchApplicationById(applicationId)
  }

  def requestApplicationDeletion(requester: UserSession, application: ApplicationWithCollaborators)(implicit hc: HeaderCarrier): Future[Option[String]] = {

    val requesterName    = requester.developer.displayedName
    val requesterEmail   = requester.developer.email
    val environment      = application.deployedTo
    val requesterRole    = roleForApplication(application, requesterEmail)
    val appId            = application.id
    val deleteRestricted = !(application.details.deleteRestriction == DeleteRestriction.NoRestriction)

    if (requesterRole.isAdministrator) {
      val deskproTicket =
        CreateTicketRequest.createForRequestApplicationDeletion(requesterName, requesterEmail, requesterRole, environment, application.name, appId, deleteRestricted)

      for {
        ticketResponse <- apiPlatformDeskproConnector.createTicket(deskproTicket, hc)
        _              <- auditService.audit(
                            ApplicationDeletionRequested,
                            Map(
                              "appId"                   -> appId.toString(),
                              "requestedByName"         -> requesterName,
                              "requestedByEmailAddress" -> requesterEmail.text,
                              "timestamp"               -> instant().toString
                            )
                          )
      } yield ticketResponse
    } else {
      Future.failed(new ForbiddenException("Developer cannot request to delete a production application"))
    }
  }

  def deleteSubordinateApplication(requester: UserSession, application: ApplicationWithCollaborators)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {

    val requesterEmail   = requester.developer.email
    val environment      = application.deployedTo
    val requesterRole    = roleForApplication(application, requesterEmail)
    val reasons          = "Subordinate application deleted by DevHub user"
    val instigator       = requester.developer.userId
    val deleteRestricted = !(application.details.deleteRestriction == DeleteRestriction.NoRestriction)

    if (environment == Environment.SANDBOX && requesterRole == Collaborator.Roles.ADMINISTRATOR && application.access.accessType == AccessType.STANDARD && !deleteRestricted) {

      val deleteRequest = ApplicationCommands.DeleteApplicationByCollaborator(instigator, reasons, instant())
      dispatchCmd(application.id, deleteRequest)

    } else {
      Future.failed(new ForbiddenException("Only standard subordinate applications can be deleted by admins"))
    }
  }

  private def roleForApplication(application: ApplicationWithCollaborators, email: LaxEmailAddress) =
    application.collaborators.find(_.emailAddress == email).getOrElse(throw new ApplicationNotFound).role

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse] = {
    thirdPartyOrchestratorConnector.verify(verificationCode)
  }

  def requestDeveloperAccountDeletion(userId: UserId, name: String, email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val deleteDeveloperTicket = CreateTicketRequest.deleteDeveloperAccount(name, email)

    for {
      ticketResponse <- apiPlatformDeskproConnector.createTicket(deleteDeveloperTicket, hc)
      _              <- auditService.audit(AccountDeletionRequested, Map("requestedByName" -> name, "requestedByEmailAddress" -> email.text, "timestamp" -> instant().toString))
    } yield ticketResponse
  }

  def request2SVRemoval(userId: UserId, name: String, email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val remove2SVTicket = CreateTicketRequest.removeDeveloper2SV(name, email)

    for {
      ticketResponse <- apiPlatformDeskproConnector.createTicket(remove2SVTicket, hc)
      _              <- auditService.audit(Remove2SVRequested, Map("requestedByEmailAddress" -> email.text, "timestamp" -> instant().toString))
    } yield ticketResponse
  }

  def isApplicationNameValid(name: String, environment: Environment, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier)
      : Future[ApplicationNameValidationResult] = {
    if (ValidatedApplicationName.validate(name).isInvalid) {
      Future.successful(ApplicationNameValidationResult.Invalid)
    }
    thirdPartyOrchestratorConnector.validateName(name, selfApplicationId, environment)
  }

  def requestProductonApplicationNameChange(
      userId: UserId,
      application: ApplicationWithCollaborators,
      newApplicationName: ApplicationName,
      requesterName: String,
      requesterEmail: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ) = {

    def createDeskproTicket(application: ApplicationWithCollaborators, newApplicationName: ApplicationName, requesterName: String, requesterEmail: LaxEmailAddress) = {
      val previousAppName = application.name
      val appId           = application.id

      CreateTicketRequest.createForRequestChangeOfProductionApplicationName(requesterName, requesterEmail, previousAppName, newApplicationName, appId)
    }

    val ticket = createDeskproTicket(application, newApplicationName, requesterName, requesterEmail)
    apiPlatformDeskproConnector.createTicket(ticket, hc)
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
}
