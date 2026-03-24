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
import scala.concurrent.{ExecutionContext, Future}

import views.html.manageapplication.{ApplicationDetailsView, ChangeAppNameAndDescView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.{Access, AccessType}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ValidatedApplicationName
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.ApplicationNameValidationResult
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommand, ApplicationCommands}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Conversions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.{Agreement, TermsOfUseViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.{appNameField, applicationNameAlreadyExistsKey, applicationNameInvalidKey}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseV2State._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.SandboxOnly
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
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
    val changeAppNameAndDescView: ChangeAppNameAndDescView,
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
          List(ApplicationCommands.ChangeSandboxApplicationName(actor, instant(), validateAppName.toOption.get))
        else
          List.empty
      },
      if (effectiveDescription == application.details.description) {
        List.empty
      } else {
        if (effectiveDescription.isDefined)
          List(ApplicationCommands.ChangeSandboxApplicationDescription(actor, instant(), effectiveDescription.get))
        else
          List(ApplicationCommands.ClearSandboxApplicationDescription(actor, instant()))
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

    val latestTermsOfUseAgreementDetails = termsOfUseService.getAgreementDetails(application).lastOption

    val requiresTermsOfUse = !application.deployedTo.isSandbox && application.access.accessType == AccessType.STANDARD

    // Query both services to derive V2 state
    for {
      maybeInvitation <- termsOfUseInvitationService.fetchTermsOfUseInvitation(application.id)
      maybeSubmission <- submissionService.fetchLatestSubmission(application.id)
    } yield {
      val termsOfUseV2State =
        (requiresTermsOfUse, maybeSubmission) match {
          case (true, None) =>
            // No submission - check for invitation
            maybeInvitation match {
              case Some(invitation) => Some(NotStarted(Some(invitation.dueBy)))
              case None             =>
                // No invitation either - check if V1 agreement exists
                // If V1 only (no V2 journey), return None to display V1 in view
                // If no V1, show NotStarted to prompt V2 acceptance
                latestTermsOfUseAgreementDetails match {
                  case Some(TermsOfUseAgreementDetails(_, _, _, Some(_))) => None                   // V1 exists, no V2 journey - let view display V1 todo is that even possible?
                  case _                                                  => Some(NotStarted(None)) // No V1, show V2 not started todo is that even possible too? No V1, no V2 invitation - if possible, what do we display here
                }
            }

          case (true, Some(submission: Submission)) =>
            val status = submission.status
            status match {
              case s if s.isCreated || s.isAnswering =>
                val requestedBy = extractRequestedByFromHistory(submission)
                // todo is it possible to have a submission without an invitation? Clarify business rules
                val deadline    = maybeInvitation.map(_.dueBy).getOrElse(instant())
                Some(Started(requestedBy, deadline))

              case submitted: Submission.Status.Submitted =>
                Some(Submitted(submitted.requestedBy, submitted.timestamp))

              case s if s.isGranted || s.isGrantedWithWarnings =>
                extractSubmittedFromHistory(submission)
                  .map(submitted => Approved(submitted.requestedBy, submitted.timestamp))

              case _ =>
                None
            }

          case (false, _) => None

        }

      latestTermsOfUseAgreementDetails match {
        case Some(TermsOfUseAgreementDetails(emailAddress, maybeName, date, maybeVersionString)) =>
          val maybeVersion = maybeVersionString.flatMap(TermsOfUseVersion.fromVersionString)
          TermsOfUseViewModel(
            requiresTermsOfUse,
            maybeVersion.contains(TermsOfUseVersion.OLD_JOURNEY),
            Some(Agreement(maybeName.getOrElse(emailAddress.text), date)),
            termsOfUseV2State
          )
        case _                                                                                   =>
          TermsOfUseViewModel(requiresTermsOfUse, false, None, termsOfUseV2State) // todo check when would that happen?
      }
    }
  }

  private def extractRequestedByFromHistory(submission: Submission): String = {
    submission.latestInstance.statusHistory.toList
      .find(_.isCreated)
      .collect {
        case created: Submission.Status.Created => created.requestedBy // todo is this case check needed?
      }
      .getOrElse("unknown")
  }

  private def extractSubmittedFromHistory(submission: Submission): Option[Submission.Status.Submitted] = {
    submission.latestInstance.statusHistory.toList
      .find(_.isSubmitted)
      .collect {
        case submitted: Submission.Status.Submitted => submitted // todo is this case check pattern match/collect needed?
      }
  }
}
