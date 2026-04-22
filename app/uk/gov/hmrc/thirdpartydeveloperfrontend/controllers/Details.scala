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

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.html._
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView
import views.html.manageapplication.ChangeAppNameAndDescView

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, ApplicationWithCollaborators, ValidatedApplicationName}
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommand, ApplicationCommands}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Conversions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.ApplicationNameModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.{appNameField, applicationNameAlreadyExistsKey, applicationNameInvalidKey}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseV2State
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{ProductionAndAdmin, SandboxOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

object Details {
  case class Agreement(who: String, when: Instant)

  case class TermsOfUseViewModel(
      required: Boolean,
      appUsesOldVersion: Boolean,
      agreement: Option[Agreement],
      termsOfUseV2State: Option[TermsOfUseV2State] = None
    ) {
    lazy val agreementNeeded = required && !agreement.isDefined
  }
  case class ApplicationNameModel(application: ApplicationWithCollaborators)
  case class PrivacyPolicyLocationModel(applicationId: ApplicationId, privacyPolicyUrl: String, isInDesktop: Boolean)
}

@Singleton
class Details @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val clock: Clock,
    unauthorisedAppDetailsView: UnauthorisedAppDetailsView,
    val changeAppNameAndDescView: ChangeAppNameAndDescView,
    changeDetailsView: ChangeDetailsView,
    requestChangeOfApplicationNameView: RequestChangeOfApplicationNameView,
    changeOfApplicationNameConfirmationView: ChangeOfApplicationNameConfirmationView,
    updatePrivacyPolicyLocationView: UpdatePrivacyPolicyLocationView,
    updateTermsAndConditionsLocationView: UpdateTermsAndConditionsLocationView,
    val fraudPreventionConfig: FraudPreventionConfig,
    submissionService: SubmissionService,
    termsOfUseService: TermsOfUseService
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with FraudPreventionNavLinkHelper
    with WithUnsafeDefaultFormBinding
    with ClockNow {

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
        changeAppNameAndDescErrorView(application.id, formWithErrors, applicationViewModelFromApplicationRequest())

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

  private def changeAppNameAndDescErrorView(
      id: ApplicationId,
      form: Form[ChangeAppNameAndDescForm],
      applicationViewModel: ApplicationViewModel
    )(implicit request: ApplicationRequest[_]
    ): Future[Result] =
    Future.successful(BadRequest(changeAppNameAndDescView(form, applicationViewModel)))

  def canChangeDetailsAndIsApprovedAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsDetails, SandboxOnly)(applicationId)(fun)

  def changeDetails(applicationId: ApplicationId): Action[AnyContent] = canChangeDetailsAndIsApprovedAction(applicationId) { implicit request =>
    Future.successful(Ok(changeDetailsView(EditApplicationForm.withData(request.application), applicationViewModelFromApplicationRequest())))
  }

  private def deriveCommands(form: EditApplicationForm)(implicit request: ApplicationRequest[AnyContent]): List[ApplicationCommand] = {
    val application             = request.application
    val actor                   = Actors.AppCollaborator(request.userRequest.developer.email)
    val access: Access.Standard = (application.access match { case s: Access.Standard => s }) // Only standard apps attempt this function

    List(
      if (form.privacyPolicyUrl == access.privacyPolicyUrl) {
        List.empty
      } else {
        form.privacyPolicyUrl match {
          case Some(ppu) => List(ApplicationCommands.ChangeSandboxApplicationPrivacyPolicyUrl(actor, instant, ppu))
          case None      => List(ApplicationCommands.RemoveSandboxApplicationPrivacyPolicyUrl(actor, instant))
        }
      },
      if (form.termsAndConditionsUrl == access.termsAndConditionsUrl) {
        List.empty
      } else {
        form.termsAndConditionsUrl match {
          case Some(tcu) => List(ApplicationCommands.ChangeSandboxApplicationTermsAndConditionsUrl(actor, instant, tcu))
          case None      => List(ApplicationCommands.RemoveSandboxApplicationTermsAndConditionsUrl(actor, instant))
        }
      }
    ).flatten

  }

  def changeDetailsAction(applicationId: ApplicationId): Action[AnyContent] =
    canChangeDetailsAndIsApprovedAction(applicationId) { implicit request: ApplicationRequest[AnyContent] =>
      val application = request.application

      def handleValidForm(form: EditApplicationForm): Future[Result] = {
        val cmds = deriveCommands(form)
        val futs = Future.sequence(cmds.map(c => applicationService.dispatchCmd(applicationId, c)))

        futs.map(_ =>
          Redirect(routes.ManageApplicationController.applicationDetails(applicationId))
        )
      }

      def handleInvalidForm(formWithErrors: Form[EditApplicationForm]): Future[Result] =
        errorView(application.id, formWithErrors, applicationViewModelFromApplicationRequest())

      EditApplicationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
    }

  def canChangeProductionDetailsAndIsApprovedAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsDetails, ProductionAndAdmin)(applicationId)(fun)

  def updatePrivacyPolicyLocation(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
    val application = request.application
    application.access match {
      case Access.Standard(_, _, _, _, _, _, Some(ImportantSubmissionData(_, _, _, _, privacyPolicyLocation, _))) =>
        Future.successful(Ok(updatePrivacyPolicyLocationView(ChangeOfPrivacyPolicyLocationForm.withNewJourneyData(privacyPolicyLocation), applicationId)))
      case Access.Standard(_, _, _, maybePrivacyPolicyUrl, _, _, None)                                            =>
        Future.successful(Ok(updatePrivacyPolicyLocationView(ChangeOfPrivacyPolicyLocationForm.withOldJourneyData(maybePrivacyPolicyUrl), applicationId)))
      case _                                                                                                      => Future.successful(BadRequest)
    }
  }

  def updatePrivacyPolicyLocationAction(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: ChangeOfPrivacyPolicyLocationForm): Future[Result] = {
      val requestForm = ChangeOfPrivacyPolicyLocationForm.form.bindFromRequest()

      val oldLocation = application.access match {
        case Access.Standard(_, _, _, _, _, _, Some(ImportantSubmissionData(_, _, _, _, privacyPolicyLocation, _))) => privacyPolicyLocation
        case Access.Standard(_, _, _, Some(privacyPolicyUrl), _, _, None)                                           => PrivacyPolicyLocations.Url(privacyPolicyUrl)
        case _                                                                                                      => PrivacyPolicyLocations.NoneProvided
      }
      val newLocation = form.toLocation

      val locationHasChanged = oldLocation != newLocation
      if (!locationHasChanged) {
        def unchangedUrlForm: Form[ChangeOfPrivacyPolicyLocationForm] = requestForm.withError(appNameField, "application.privacypolicylocation.invalid.unchanged")
        Future.successful(BadRequest(updatePrivacyPolicyLocationView(unchangedUrlForm, applicationId)))

      } else {
        applicationService.updatePrivacyPolicyLocation(application, request.userId, newLocation).map(_ =>
          Redirect(routes.ManageApplicationController.applicationDetails(applicationId))
        )
      }
    }

    def handleInvalidForm(formWithErrors: Form[ChangeOfPrivacyPolicyLocationForm]): Future[Result] = {
      Future.successful(BadRequest(updatePrivacyPolicyLocationView(formWithErrors, applicationId)))
    }

    ChangeOfPrivacyPolicyLocationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def updateTermsAndConditionsLocation(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
    val application = request.application
    application.access match {
      case Access.Standard(_, _, _, _, _, _, Some(ImportantSubmissionData(_, _, _, termsAndConditionsLocation, _, _))) =>
        Future.successful(Ok(updateTermsAndConditionsLocationView(ChangeOfTermsAndConditionsLocationForm.withNewJourneyData(termsAndConditionsLocation), applicationId)))
      case Access.Standard(_, _, maybeTermsAndConditionsUrl, _, _, _, None)                                            =>
        Future.successful(Ok(updateTermsAndConditionsLocationView(ChangeOfTermsAndConditionsLocationForm.withOldJourneyData(maybeTermsAndConditionsUrl), applicationId)))
      case _                                                                                                           => Future.successful(BadRequest)
    }
  }

  def updateTermsAndConditionsLocationAction(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: ChangeOfTermsAndConditionsLocationForm): Future[Result] = {
      val requestForm = ChangeOfTermsAndConditionsLocationForm.form.bindFromRequest()

      val oldLocation = application.access match {
        case Access.Standard(_, _, _, _, _, _, Some(ImportantSubmissionData(_, _, _, termsAndConditionsLocation, _, _))) => termsAndConditionsLocation
        case Access.Standard(_, _, Some(termsAndConditionsUrl), _, _, _, None)                                           => TermsAndConditionsLocations.Url(termsAndConditionsUrl)
        case _                                                                                                           => PrivacyPolicyLocations.NoneProvided
      }
      val newLocation = form.toLocation

      val locationHasChanged = oldLocation != newLocation
      if (!locationHasChanged) {
        def unchangedUrlForm: Form[ChangeOfTermsAndConditionsLocationForm] = requestForm.withError(appNameField, "application.termsconditionslocation.invalid.unchanged")
        Future.successful(BadRequest(updateTermsAndConditionsLocationView(unchangedUrlForm, applicationId)))

      } else {
        applicationService.updateTermsConditionsLocation(application, request.userId, newLocation).map(_ =>
          Redirect(routes.ManageApplicationController.applicationDetails(applicationId))
        )
      }
    }

    def handleInvalidForm(formWithErrors: Form[ChangeOfTermsAndConditionsLocationForm]): Future[Result] = {
      Future.successful(BadRequest(updateTermsAndConditionsLocationView(formWithErrors, applicationId)))
    }

    ChangeOfTermsAndConditionsLocationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  private def errorView(id: ApplicationId, form: Form[EditApplicationForm], applicationViewModel: ApplicationViewModel)(implicit request: ApplicationRequest[_]): Future[Result] =
    Future.successful(BadRequest(changeDetailsView(form, applicationViewModel)))

  def requestChangeOfAppName(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
    Future.successful(Ok(requestChangeOfApplicationNameView(ChangeOfApplicationNameForm.withData(request.application.name), ApplicationNameModel(request.application))))
  }

  def requestChangeOfAppNameAction(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: ChangeOfApplicationNameForm): Future[Result] = {
      val requestForm        = ChangeOfApplicationNameForm.form.bindFromRequest()
      val newApplicationName = form.applicationName.trim()

      if (newApplicationName.equalsIgnoreCase(application.name.value)) {

        def unchangedNameCheckForm: Form[ChangeOfApplicationNameForm] =
          requestForm.withError(appNameField, "application.name.unchanged.error")
        Future.successful(BadRequest(requestChangeOfApplicationNameView(unchangedNameCheckForm, ApplicationNameModel(request.application))))

      } else {

        applicationService
          .isApplicationNameValid(newApplicationName, application.deployedTo, Some(applicationId))
          .flatMap({

            case ApplicationNameValidationResult.Valid =>
              for {
                _ <-
                  applicationService.requestProductonApplicationNameChange(
                    request.userSession.developer.userId,
                    application,
                    ApplicationName(newApplicationName),
                    request.userSession.developer.displayedName,
                    request.userSession.developer.email
                  )
              } yield Ok(changeOfApplicationNameConfirmationView(ApplicationNameModel(request.application), newApplicationName))

            case ApplicationNameValidationResult.Invalid =>
              Future.successful(BadRequest(requestChangeOfApplicationNameView(
                requestForm.withError(appNameField, applicationNameInvalidKey),
                ApplicationNameModel(request.application)
              )))

            case ApplicationNameValidationResult.Duplicate =>
              Future.successful(BadRequest(requestChangeOfApplicationNameView(
                requestForm.withError(appNameField, applicationNameAlreadyExistsKey),
                ApplicationNameModel(request.application)
              )))
          })
      }
    }

    def handleInvalidForm(formWithErrors: Form[ChangeOfApplicationNameForm]): Future[Result] =
      Future.successful(BadRequest(requestChangeOfApplicationNameView(formWithErrors, ApplicationNameModel(request.application))))

    ChangeOfApplicationNameForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

}
