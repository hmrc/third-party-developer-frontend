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

package controllers

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers.FormKeys.appNameField
import domain.Capabilities.SupportsDetails
import domain.Permissions.SandboxOrAdmin
import domain._
import javax.inject.{Inject, Singleton}
import model.ApplicationViewModel
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import service._
import controllers.checkpages.{CheckYourAnswersData, CheckYourAnswersForm, DummyCheckYourAnswersForm}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Details @Inject()(developerConnector: ThirdPartyDeveloperConnector,
                        auditService: AuditService,
                        val applicationService: ApplicationService,
                        val sessionService: SessionService,
                        val errorHandler: ErrorHandler,
                        val messagesApi: MessagesApi,
                        val cookieSigner : CookieSigner
                        )
                       (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController {

  def canChangeDetailsAction(applicationId: String)
                               (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsAction(SupportsDetails,SandboxOrAdmin)(applicationId)(fun)


  def details(applicationId: String): Action[AnyContent] = whenTeamMemberOnApp(applicationId) { implicit request =>
      val checkYourAnswersData = CheckYourAnswersData(request.application, request.subscriptions)

    (request.application.state.name) match {
      case State.TESTING => {
        if (request.role.isAdministrator){
          Future.successful(Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(request.application.id)))
        } else {
          Future.successful(Ok(views.html.checkpages.applicationcheck.unauthorisedAppDetails(request.application.name, request.application.adminEmails)))
        }
      }
      case State.PENDING_GATEKEEPER_APPROVAL | State.PENDING_REQUESTER_VERIFICATION => 
        Future.successful(Ok(views.html.application.pendingApproval(checkYourAnswersData, CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy")))))
      case State.PRODUCTION => Future.successful(Ok(views.html.details(applicationViewModelFromApplicationRequest)))
    }
  }

  def changeDetails(applicationId: String): Action[AnyContent] = canChangeDetailsAction(applicationId) { implicit request =>
    Future.successful(Ok(views.html.changeDetails(EditApplicationForm.withData(request.application), applicationViewModelFromApplicationRequest)))
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

  def changeDetailsAction(applicationId: String): Action[AnyContent] =
    canChangeDetailsAction(applicationId) { implicit request: ApplicationRequest[AnyContent] =>
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

        applicationService.isApplicationNameValid(form.applicationName, application.deployedTo, Some(applicationId))
          .flatMap({

            case Valid =>
              val updateRequest = UpdateApplicationRequest.from(form, application)
              for {
                _ <- updateApplication(updateRequest)
                _ <- updateCheckInformation(updateRequest)
              } yield Redirect(controllers.routes.Details.details(applicationId))

            case invalid : Invalid =>
              def invalidNameCheckForm: Form[EditApplicationForm] =
                requestForm.withError(appNameField, invalid.validationErrorMessageKey)

              Future.successful(BadRequest(views.html.changeDetails(invalidNameCheckForm, applicationViewModelFromApplicationRequest)))
          })
      }

      def handleInvalidForm(formWithErrors: Form[EditApplicationForm]): Future[Result] =
        errorView(application.id, formWithErrors, applicationViewModelFromApplicationRequest)

      EditApplicationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
    }

  private def errorView(id: String, form: Form[EditApplicationForm], applicationViewModel: ApplicationViewModel)
                       (implicit request: ApplicationRequest[_]): Future[Result] =
    Future.successful(BadRequest(views.html.changeDetails(form, applicationViewModel)))
}
