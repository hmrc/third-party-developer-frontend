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
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.SandboxOrAdmin

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import views.html.{ChangeDetailsView, DetailsView}
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
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.TermsOfUseViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion

object Details {
  case class TermsOfUseViewModel(
    exists: Boolean,
    agreed: Boolean,
    agreementNeeded: Boolean,
    appUsesOldVersion: Boolean,
    emailAddressOfWhoAgreed: String,
    timestampOfWhenAgreed: DateTime
  )
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
    val fraudPreventionConfig: FraudPreventionConfig,
    submissionService: SubmissionService,
    termsOfUseVersionService: TermsOfUseVersionService
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc) with FraudPreventionNavLinkHelper {

  def canChangeDetailsAndIsApprovedAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsDetails, SandboxOrAdmin)(applicationId)(fun)

  def details(applicationId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(applicationId) { implicit request =>
    val accessLevel = DevhubAccessLevel.fromRole(request.role)
    val checkYourAnswersData = CheckYourAnswersData(accessLevel, request.application, request.subscriptions)


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

      case State.PENDING_GATEKEEPER_APPROVAL | State.PENDING_REQUESTER_VERIFICATION => {
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

      case State.PRODUCTION =>
        successful(
          Ok(
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
        )
    }
  }

  private def buildTermsOfUseViewModel()(implicit request: ApplicationRequest[AnyContent]): TermsOfUseViewModel = {
    val application = request.application
    val hasTermsOfUse = application.termsOfUseStatus != TermsOfUseStatus.NOT_APPLICABLE
    val termsOfUseAgreed = application.termsOfUseStatus == TermsOfUseStatus.AGREED
    val termsOfUseAgreementRequired = application.termsOfUseStatus == TermsOfUseStatus.AGREEMENT_REQUIRED
    val appUsesOldTermsOfUse = termsOfUseVersionService.getForApplication(application) == TermsOfUseVersion.V1_2
    val emailAddress = "email@example.com" //TODO
    val timestamp = DateTime.now //TODO

    TermsOfUseViewModel(hasTermsOfUse, termsOfUseAgreed, termsOfUseAgreementRequired, appUsesOldTermsOfUse, emailAddress, timestamp)
  }

  def changeDetails(applicationId: ApplicationId): Action[AnyContent] = canChangeDetailsAndIsApprovedAction(applicationId) { implicit request =>
    Future.successful(Ok(changeDetailsView(EditApplicationForm.withData(request.application), applicationViewModelFromApplicationRequest)))
  }

  private def buildCheckInformation(updateRequest: UpdateApplicationRequest, application: Application): CheckInformation = {
    val updatedAccess = updateRequest.access.asInstanceOf[Standard]
    val access = application.access.asInstanceOf[Standard]

    def confirmedNameValue(checkInformation: CheckInformation): Boolean =
      updateRequest.name == application.name && checkInformation.confirmedName

    def providedPrivacyPolicyUrlValue(checkInformation: CheckInformation): Boolean = {
      updatedAccess.privacyPolicyUrl == access.privacyPolicyUrl && checkInformation.providedPrivacyPolicyURL
    }

    def providedTermsAndConditionsUrlValue(checkInformation: CheckInformation): Boolean = {
      updatedAccess.termsAndConditionsUrl == access.termsAndConditionsUrl && checkInformation.providedTermsAndConditionsURL
    }

    val checkInformation = application.checkInformation.getOrElse(CheckInformation())

    CheckInformation(
      confirmedName = confirmedNameValue(checkInformation),
      contactDetails = checkInformation.contactDetails,
      providedPrivacyPolicyURL = providedPrivacyPolicyUrlValue(checkInformation),
      providedTermsAndConditionsURL = providedTermsAndConditionsUrlValue(checkInformation),
      termsOfUseAgreements = checkInformation.termsOfUseAgreements
    )
  }

  private def updateApplication(updateRequest: UpdateApplicationRequest)
                               (implicit request: ApplicationRequest[AnyContent]): Future[ApplicationUpdateSuccessful] = {
    applicationService.update(updateRequest)
  }

  def changeDetailsAction(applicationId: ApplicationId): Action[AnyContent] =
    canChangeDetailsAndIsApprovedAction(applicationId) { implicit request: ApplicationRequest[AnyContent] =>
      val application = request.application

      def updateCheckInformation(updateRequest: UpdateApplicationRequest): Future[ApplicationUpdateSuccessful] = {
        if (application.deployedTo.isProduction()) {
          applicationService.updateCheckInformation(application, buildCheckInformation(updateRequest, application))
        } else {
          Future.successful(ApplicationUpdateSuccessful)
        }
      }

      def handleValidForm(form: EditApplicationForm): Future[Result] = {
        val requestForm = EditApplicationForm.form.bindFromRequest

        applicationService
          .isApplicationNameValid(form.applicationName, application.deployedTo, Some(applicationId))
          .flatMap({

            case Valid =>
              val updateRequest = UpdateApplicationRequest.from(form, application)
              for {
                _ <- updateApplication(updateRequest)
                _ <- updateCheckInformation(updateRequest)
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

  private def errorView(id: ApplicationId, form: Form[EditApplicationForm], applicationViewModel: ApplicationViewModel)(implicit request: ApplicationRequest[_]): Future[Result] =
    Future.successful(BadRequest(changeDetailsView(form, applicationViewModel)))
}
