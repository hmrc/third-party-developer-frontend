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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import cats.data.OptionT
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView
import views.html.manageapplication.{ApplicationDetailsView, ChangeAppNameAndDescView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.{Access, AccessType}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{State, ValidatedApplicationName}
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.ApplicationNameValidationResult
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommand, ApplicationCommands}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId, LaxEmailAddress}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Conversions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.{Agreement, TermsOfUseViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.{appNameField, applicationNameAlreadyExistsKey, applicationNameInvalidKey}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseV2State._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.SandboxOnly
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.{TermsOfUseV2State, TermsOfUseVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

@Singleton
class ManageApplicationController @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    val fraudPreventionConfig: FraudPreventionConfig,
    val termsOfUseService: TermsOfUseService,
    submissionService: SubmissionService,
    termsOfUseInvitationService: TermsOfUseInvitationService,
    profileService: ProfileService,
    val changeAppNameAndDescView: ChangeAppNameAndDescView,
    unauthorisedAppDetailsView: UnauthorisedAppDetailsView,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val clock: Clock,
    applicationDetailsView: ApplicationDetailsView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with FraudPreventionNavLinkHelper
    with ClockNow {

  def applicationDetails(applicationId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(applicationId) { implicit request =>
    def appDetailsPage =
      buildTermsOfUseViewModel().map { termsOfUseViewModel =>
        Ok(applicationDetailsView(
          applicationViewModelFromApplicationRequest(),
          request.subscriptions,
          createOptionalFraudPreventionNavLinkViewModel(
            request.application,
            request.subscriptions,
            fraudPreventionConfig
          ),
          termsOfUseViewModel
        ))
      }

    request.application.state.name match {
      case State.TESTING =>
        lazy val oldJourney = BadRequest("You can no longer view or update an old production credentials request.")

        lazy val newUpliftJourney = (s: Submission) =>
          if (request.role.isAdministrator) {
            if (s.status.isAnsweredCompletely) {
              Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(applicationId))
            } else {
              Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(applicationId))
            }
          } else {
            Ok(unauthorisedAppDetailsView(request.application.name, request.application.admins))
          }

        OptionT(submissionService.fetchLatestSubmission(applicationId)).fold(oldJourney)(newUpliftJourney)

      case State.PENDING_RESPONSIBLE_INDIVIDUAL_VERIFICATION | State.PENDING_GATEKEEPER_APPROVAL | State.PENDING_REQUESTER_VERIFICATION => {
        lazy val oldJourney = BadRequest("You can no longer view or update an old production credentials request.")

        lazy val newUpliftJourney = (s: Submission) =>
          Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CredentialsRequestedController.credentialsRequestedPage(applicationId))

        OptionT(submissionService.fetchLatestSubmission(applicationId)).fold(oldJourney)(newUpliftJourney)
      }

      case State.PRE_PRODUCTION =>
        request.queryString.contains("forceAppDetails") match {
          case true  => appDetailsPage
          case false =>
            successful(Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.StartUsingYourApplicationController.startUsingYourApplicationPage(applicationId)))
        }

      case State.PRODUCTION =>
        appDetailsPage

      case State.DELETED =>
        successful(BadRequest)
    }
  }

  def changeAppNameAndDesc(applicationId: ApplicationId): Action[AnyContent] = canChangeDetailsAndIsApprovedAction(applicationId) { implicit request =>
    Future.successful(Ok(changeAppNameAndDescView(ChangeAppNameAndDescForm.withData(request.application), applicationViewModelFromApplicationRequest())))
  }

  def changeAppNameAndDescAction(applicationId: ApplicationId): Action[AnyContent] =
    canChangeDetailsAndIsApprovedAction(applicationId) { implicit request: ApplicationRequest[AnyContent] =>
      val application = request.application

      def handleValidForm(form: ChangeAppNameAndDescForm): Future[Result] = {
        val requestForm: Form[ChangeAppNameAndDescForm] = ChangeAppNameAndDescForm.form.bindFromRequest()

        applicationService
          .isApplicationNameValid(form.applicationName, application.deployedTo, Some(applicationId))
          .flatMap({
            case ApplicationNameValidationResult.Valid =>
              val cmds = deriveCommands(form)
              val futs = Future.sequence(cmds.map(c => applicationService.dispatchCmd(applicationId, c)))

              futs.map(_ =>
                Redirect(routes.ManageApplicationController.applicationDetails(applicationId))
              )

            case ApplicationNameValidationResult.Invalid =>
              Future.successful(BadRequest(changeAppNameAndDescView(
                requestForm.withError(appNameField, applicationNameInvalidKey),
                applicationViewModelFromApplicationRequest()
              )))

            case ApplicationNameValidationResult.Duplicate =>
              Future.successful(BadRequest(changeAppNameAndDescView(
                requestForm.withError(appNameField, applicationNameAlreadyExistsKey),
                applicationViewModelFromApplicationRequest()
              )))
          })
      }

      def handleInvalidForm(formWithErrors: Form[ChangeAppNameAndDescForm]): Future[Result] =
        errorView(application.id, formWithErrors, applicationViewModelFromApplicationRequest())

      ChangeAppNameAndDescForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
    }

  private def deriveCommands(form: ChangeAppNameAndDescForm)(implicit request: ApplicationRequest[AnyContent]): List[ApplicationCommand] = {
    val application             = request.application
    val actor                   = Actors.AppCollaborator(request.userRequest.developer.email)
    val access: Access.Standard = (application.access match { case s: Access.Standard => s }) // Only standard apps attempt this function

    val effectiveNewName     = if (application.isInTesting || application.deployedTo.isSandbox) {
      form.applicationName.trim
    } else {
      application.name.value
    }
    val effectiveDescription = form.description.filterNot(_.isBlank()).map(desc => desc.trim)

    List(
      if (effectiveNewName == application.name.value)
        List.empty
      else {
        val validateAppName = ValidatedApplicationName.validate(effectiveNewName)
        if (validateAppName.isValid) // This has already been validated
          List(ApplicationCommands.ChangeSandboxApplicationName(actor, instant, validateAppName.toOption.get))
        else
          List.empty
      },
      if (effectiveDescription == application.details.description) {
        List.empty
      } else {
        if (effectiveDescription.isDefined)
          List(ApplicationCommands.ChangeSandboxApplicationDescription(actor, instant, effectiveDescription.get))
        else
          List(ApplicationCommands.ClearSandboxApplicationDescription(actor, instant))
      }
    ).flatten

  }

  private def canChangeDetailsAndIsApprovedAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsDetails, SandboxOnly)(applicationId)(fun)

  private def errorView(id: ApplicationId, form: Form[ChangeAppNameAndDescForm], applicationViewModel: ApplicationViewModel)(implicit request: ApplicationRequest[_]): Future[Result] =
    Future.successful(BadRequest(changeAppNameAndDescView(form, applicationViewModel)))

  private def buildTermsOfUseViewModel()(implicit request: ApplicationRequest[AnyContent]): Future[TermsOfUseViewModel] = {
    implicit val hc: uk.gov.hmrc.http.HeaderCarrier = super.hc(request)
    val application                                 = request.application
    val requiresTermsOfUse                          = !application.deployedTo.isSandbox && application.access.accessType == AccessType.STANDARD

    if (requiresTermsOfUse) {
      val latestTermsOfUseAgreementDetails = termsOfUseService.getAgreementDetails(application).lastOption
      val (appUsesOldVersion, agreement)   = buildAgreementData(latestTermsOfUseAgreementDetails)

      for {
        termsOfUseV2State <- buildV2TermsOfUseState(application.id, latestTermsOfUseAgreementDetails)
      } yield {
        TermsOfUseViewModel(requiresTermsOfUse, appUsesOldVersion, agreement, termsOfUseV2State)
      }
    } else {
      Future.successful(TermsOfUseViewModel(false, false, None, None))
    }
  }

  private def buildAgreementData(latestTermsOfUseAgreementDetails: Option[TermsOfUseAgreementDetails]): (Boolean, Option[Agreement]) = {
    latestTermsOfUseAgreementDetails match {
      case Some(TermsOfUseAgreementDetails(emailAddress, maybeName, date, maybeVersionString)) =>
        val maybeVersion      = maybeVersionString.flatMap(TermsOfUseVersion.fromVersionString)
        val appUsesOldVersion = maybeVersion.contains(TermsOfUseVersion.OLD_JOURNEY)
        val agreement         = Some(Agreement(maybeName.getOrElse(emailAddress.text), date))
        (appUsesOldVersion, agreement)
      case _                                                                                   =>
        (false, None)
    }
  }

  private def buildV2TermsOfUseState(
      applicationId: ApplicationId,
      latestTermsOfUseAgreementDetails: Option[TermsOfUseAgreementDetails]
    )(implicit hc: uk.gov.hmrc.http.HeaderCarrier
    ): Future[Option[TermsOfUseV2State]] = {
    for {
      maybeInvitation <- termsOfUseInvitationService.fetchTermsOfUseInvitation(applicationId)
      maybeSubmission <- submissionService.fetchLatestSubmission(applicationId)
      maybeState      <- (maybeInvitation, maybeSubmission) match {
                           case (Some(invitation), None) =>
                             Future.successful(Some(NotStarted(Some(invitation.dueBy))))

                           case (None, None) =>
                             Future.successful(Some(NotStarted(Some(instant))))

                           case (maybeInv, Some(submission)) if submission.status.isCreated || submission.status.isAnswering =>
                             val requestedByEmail = extractRequestedByFromHistory(submission)
                             val deadline         = maybeInv.map(_.dueBy).getOrElse(instant)
                             profileService.lookupDeveloperName(LaxEmailAddress(requestedByEmail)).map { name =>
                               Some(Started(name, deadline))
                             }

                           case (_, Some(submission)) if submission.status.isSubmitted || submission.status.isFailed || submission.status.isWarnings || submission.status.isGrantedWithWarnings =>
                             extractSubmittedFromHistory(submission) match {
                               case Some(submitted) =>
                                 profileService.lookupDeveloperName(LaxEmailAddress(submitted.requestedBy)).map { name =>
                                   Some(Submitted(name, submitted.timestamp))
                                 }
                               case None            =>
                                 Future.successful(None)
                             }

                           case (_, Some(submission)) if submission.status.isGranted =>
                             extractSubmittedFromHistory(submission) match {
                               case Some(submitted) =>
                                 Future.successful(Some(Approved(submitted.requestedBy, submitted.timestamp)))
                               case None            =>
                                 Future.successful(None)
                             }

                           case (_, Some(_)) =>
                             Future.successful(None)
                         }
    } yield maybeState
  }

  private def extractRequestedByFromHistory(submission: Submission): String = {
    submission.latestInstance.statusHistory.toList
      .collectFirst {
        case created: Submission.Status.Created => created.requestedBy
      }
      .getOrElse("unknown")
  }

  private def extractSubmittedFromHistory(submission: Submission): Option[Submission.Status.Submitted] = {
    submission.latestInstance.statusHistory.toList
      .collectFirst {
        case submitted: Submission.Status.Submitted => submitted
      }
  }
}
