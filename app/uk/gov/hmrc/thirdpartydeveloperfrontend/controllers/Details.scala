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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.FraudPreventionConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.appNameField
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.{CheckYourAnswersData, CheckYourAnswersForm, DummyCheckYourAnswersForm}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{ProductionAndAdmin, SandboxOnly}

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import views.html.{ChangeDetailsView, DetailsView, UpdatePrivacyPolicyLocationView, RequestChangeOfApplicationNameView, ChangeOfApplicationNameConfirmationView}
import views.html.application.PendingApprovalView
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessLevel
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import cats.data.OptionT
import cats.instances.future.catsStdInstancesForFuture
import org.joda.time.DateTime

import scala.concurrent.Future.successful
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.play.bootstrap.controller.WithDefaultFormBinding
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.{Agreement, TermsOfUseViewModel, ApplicationNameModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.PrivacyPolicyLocation.NoneProvided
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails

object Details {
  case class Agreement(who: String, when: DateTime)
  case class TermsOfUseViewModel(
    exists: Boolean,
    appUsesOldVersion: Boolean,
    agreement: Option[Agreement]
  ) {
    lazy val agreementNeeded = exists && !agreement.isDefined
  }
  case class ApplicationNameModel(application: Application)
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
    unauthorisedAppDetailsView: UnauthorisedAppDetailsView,
    pendingApprovalView: PendingApprovalView,
    detailsView: DetailsView,
    changeDetailsView: ChangeDetailsView,
    requestChangeOfApplicationNameView: RequestChangeOfApplicationNameView,
    changeOfApplicationNameConfirmationView: ChangeOfApplicationNameConfirmationView,
    updatePrivacyPolicyLocationView: UpdatePrivacyPolicyLocationView,
    val fraudPreventionConfig: FraudPreventionConfig,
    submissionService: SubmissionService,
    termsOfUseService: TermsOfUseService
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc)
      with FraudPreventionNavLinkHelper
      with WithDefaultFormBinding {

  def canChangeDetailsAndIsApprovedAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsDetails, SandboxOnly)(applicationId)(fun)

  def details(applicationId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(applicationId) { implicit request =>
    val accessLevel = DevhubAccessLevel.fromRole(request.role)
    val checkYourAnswersData = CheckYourAnswersData(accessLevel, request.application, request.subscriptions)
    def appDetailsPage = Ok(
      detailsView(
        applicationViewModelFromApplicationRequest,
        buildTermsOfUseViewModel,
        createOptionalFraudPreventionNavLinkViewModel(
          request.application,
          request.subscriptions,
          fraudPreventionConfig
        )
      )
    )

    request.application.state.name match {
      case State.TESTING =>
        lazy val oldJourney =
          if (request.role.isAdministrator)
            Redirect(uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.routes.ApplicationCheck.requestCheckPage(request.application.id))
          else
            Ok(unauthorisedAppDetailsView(request.application.name, request.application.adminEmails))

        lazy val newUpliftJourney = (s: Submission) =>
          if (request.role.isAdministrator) {
            if(s.status.isAnsweredCompletely) {
              Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(applicationId))
            } else {
              Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(applicationId))
            }
          } else {
            Ok(unauthorisedAppDetailsView(request.application.name, request.application.adminEmails))
          }

        OptionT(submissionService.fetchLatestSubmission(applicationId)).fold(oldJourney)(newUpliftJourney)

      case State.PENDING_RESPONSIBLE_INDIVIDUAL_VERIFICATION | State.PENDING_GATEKEEPER_APPROVAL | State.PENDING_REQUESTER_VERIFICATION => {
        lazy val oldJourney = 
          Ok(
            pendingApprovalView(
              checkYourAnswersData,
              CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy"))
            )
          )


        lazy val newUpliftJourney = (s: Submission) =>
          Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CredentialsRequestedController.credentialsRequestedPage(applicationId))

        OptionT(submissionService.fetchLatestSubmission(applicationId)).fold(oldJourney)(newUpliftJourney)    
      }

      case State.PRE_PRODUCTION =>
        successful(request.queryString.contains("forceAppDetails") match {
          case true => appDetailsPage
          case false => Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.StartUsingYourApplicationController.startUsingYourApplicationPage(applicationId))
        })

      case State.PRODUCTION =>
        successful(appDetailsPage)
    }
  }

  private def buildTermsOfUseViewModel()(implicit request: ApplicationRequest[AnyContent]): TermsOfUseViewModel = {
    val application = request.application

    val latestTermsOfUseAgreementDetails = termsOfUseService.getAgreementDetails(application).lastOption

    val hasTermsOfUse = ! application.deployedTo.isSandbox && application.access.accessType.isStandard
    latestTermsOfUseAgreementDetails match {
      case Some(TermsOfUseAgreementDetails(emailAddress, maybeName, date, maybeVersionString)) => {
        val maybeVersion = maybeVersionString.flatMap(TermsOfUseVersion.fromVersionString(_))
        TermsOfUseViewModel(hasTermsOfUse, maybeVersion.contains(TermsOfUseVersion.OLD_JOURNEY), Some(Agreement(maybeName.getOrElse(emailAddress), date)))
      }
      case _ => TermsOfUseViewModel(hasTermsOfUse, false, None)
    }
  }

  def changeDetails(applicationId: ApplicationId): Action[AnyContent] = canChangeDetailsAndIsApprovedAction(applicationId) { implicit request =>
    Future.successful(Ok(changeDetailsView(EditApplicationForm.withData(request.application), applicationViewModelFromApplicationRequest)))
  }

  private def updateApplication(updateRequest: UpdateApplicationRequest)
                               (implicit request: ApplicationRequest[AnyContent]): Future[ApplicationUpdateSuccessful] = {
    applicationService.update(updateRequest)
  }

  def changeDetailsAction(applicationId: ApplicationId): Action[AnyContent] =
    canChangeDetailsAndIsApprovedAction(applicationId) { implicit request: ApplicationRequest[AnyContent] =>
      val application = request.application

      def handleValidForm(form: EditApplicationForm): Future[Result] = {
        val requestForm = EditApplicationForm.form.bindFromRequest

        applicationService
          .isApplicationNameValid(form.applicationName, application.deployedTo, Some(applicationId))
          .flatMap({

            case Valid =>
              val updateRequest = UpdateApplicationRequest.from(form, application)
              for {
                _ <- updateApplication(updateRequest)
              } yield Redirect(routes.Details.details(applicationId))

            case invalid: Invalid =>
              def invalidNameCheckForm: Form[EditApplicationForm] =
                requestForm.withError(appNameField, invalid.validationErrorMessageKey)

              Future.successful(BadRequest(changeDetailsView(invalidNameCheckForm, applicationViewModelFromApplicationRequest)))
          })
      }

      def handleInvalidForm(formWithErrors: Form[EditApplicationForm]): Future[Result] =
        errorView(application.id, formWithErrors, applicationViewModelFromApplicationRequest)

      EditApplicationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
    }

  def canChangeProductionDetailsAndIsApprovedAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsDetails, ProductionAndAdmin)(applicationId)(fun)

  def updatePrivacyPolicyLocation(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
    val application = request.application
    application.access match {
      case Standard(_,_,_,_,_,Some(ImportantSubmissionData(_, _, _, _, privacyPolicyLocation, _))) =>
        Future.successful(Ok(updatePrivacyPolicyLocationView(ChangeOfPrivacyPolicyLocationForm.withData(privacyPolicyLocation), applicationId)))
      case _ => Future.successful(BadRequest)
    }
  }

  def updatePrivacyPolicyLocationAction(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: ChangeOfPrivacyPolicyLocationForm): Future[Result] = {
      val requestForm = ChangeOfPrivacyPolicyLocationForm.form.bindFromRequest

      val oldLocation = application.access match {
        case Standard(_, _, _, _, _, Some(ImportantSubmissionData(_, _, _, _, privacyPolicyLocation, _))) => privacyPolicyLocation
        case _ => NoneProvided
      }
      val newLocation = form.toLocation

      val locationHasChanged = oldLocation != newLocation
      if (!locationHasChanged) {
        def unchangedUrlForm: Form[ChangeOfPrivacyPolicyLocationForm] = requestForm.withError(appNameField, "application.privacypolicylocation.invalid.unchanged")
        Future.successful(BadRequest(updatePrivacyPolicyLocationView(unchangedUrlForm, applicationId)))

      } else {
        applicationService.updatePrivacyPolicyLocation(application, request.userId, newLocation).map(_ => Redirect(routes.Details.details(applicationId)))
      }
    }

    def handleInvalidForm(formWithErrors: Form[ChangeOfPrivacyPolicyLocationForm]): Future[Result] = {
      Future.successful(BadRequest(updatePrivacyPolicyLocationView(formWithErrors, applicationId)))
    }

    ChangeOfPrivacyPolicyLocationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }


  private def errorView(id: ApplicationId, form: Form[EditApplicationForm], applicationViewModel: ApplicationViewModel)(implicit request: ApplicationRequest[_]): Future[Result] =
    Future.successful(BadRequest(changeDetailsView(form, applicationViewModel)))

  def requestChangeOfAppName(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
      Future.successful(Ok(requestChangeOfApplicationNameView(ChangeOfApplicationNameForm.withData(request.application.name), ApplicationNameModel(request.application))))
  }

  def requestChangeOfAppNameAction(applicationId: ApplicationId): Action[AnyContent] = canChangeProductionDetailsAndIsApprovedAction(applicationId) { implicit request =>
      val application = request.application

      def handleValidForm(form: ChangeOfApplicationNameForm): Future[Result] = {
        val requestForm = ChangeOfApplicationNameForm.form.bindFromRequest
        val newApplicationName = form.applicationName.trim()

        if (newApplicationName.equalsIgnoreCase(application.name)) {

          def unchangedNameCheckForm: Form[ChangeOfApplicationNameForm] =
            requestForm.withError(appNameField, "application.name.unchanged.error")
          Future.successful(BadRequest(requestChangeOfApplicationNameView(unchangedNameCheckForm, ApplicationNameModel(request.application))))

        } else {

          applicationService
            .isApplicationNameValid(newApplicationName, application.deployedTo, Some(applicationId))
            .flatMap({

              case Valid =>
                for {
                  _ <- applicationService.requestProductonApplicationNameChange(application, newApplicationName, request.developerSession.displayedName, request.developerSession.email)
                } yield Ok(changeOfApplicationNameConfirmationView(ApplicationNameModel(request.application), newApplicationName))

              case invalid: Invalid =>
                def invalidNameCheckForm: Form[ChangeOfApplicationNameForm] =
                  requestForm.withError(appNameField, invalid.validationErrorMessageKey)

                Future.successful(BadRequest(requestChangeOfApplicationNameView(invalidNameCheckForm, ApplicationNameModel(request.application))))
          })
        }
      }

      def handleInvalidForm(formWithErrors: Form[ChangeOfApplicationNameForm]): Future[Result] =
        Future.successful(BadRequest(requestChangeOfApplicationNameView(formWithErrors, ApplicationNameModel(request.application))))

      ChangeOfApplicationNameForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }  
  
}
