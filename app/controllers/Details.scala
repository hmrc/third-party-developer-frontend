/*
 * Copyright 2019 HM Revenue & Customs
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
import domain.Capabilities.SupportsDetails
import domain.Permissions.SandboxOrAdmin
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc._
import service._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Details @Inject()(developerConnector: ThirdPartyDeveloperConnector,
                        auditService: AuditService,
                        val applicationService: ApplicationService,
                        val sessionService: SessionService,
                        val errorHandler: ErrorHandler,
                        val messagesApi: MessagesApi,
                        implicit val appConfig: ApplicationConfig)
                       (implicit ec: ExecutionContext)
  extends ApplicationController {

  def canChangeDetailsAction(applicationId: String)
                               (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsAction(SupportsDetails,SandboxOrAdmin)(applicationId)(fun)

  def details(applicationId: String) = whenTeamMemberOnApp(applicationId) { implicit request =>
    Future.successful(Ok(views.html.details(request.application)))
  }

  def changeDetails(applicationId: String) = canChangeDetailsAction(applicationId) { implicit request =>
    Future.successful(Ok(views.html.changeDetails(EditApplicationForm.withData(request.application), request.application)))
  }

  def changeDetailsAction(applicationId: String) =
    canChangeDetailsAction(applicationId) { implicit request =>
      val application = request.application
      val access = application.access.asInstanceOf[Standard]

      def buildCheckInformation(updateRequest: UpdateApplicationRequest): CheckInformation = {
        val updatedAccess = updateRequest.access.asInstanceOf[Standard]

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
          applicationDetails = checkInformation.applicationDetails,
          contactDetails = checkInformation.contactDetails,
          providedPrivacyPolicyURL = providedPrivacyPolicyUrlValue(checkInformation),
          providedTermsAndConditionsURL = providedTermsAndConditionsUrlValue(checkInformation),
          termsOfUseAgreements = checkInformation.termsOfUseAgreements
        )
      }

      def updateApplication(updateRequest: UpdateApplicationRequest) = {
        applicationService.update(updateRequest)
      }

      def updateCheckInformation(updateRequest: UpdateApplicationRequest) = {
        if (application.deployedTo.isProduction) {
          applicationService.updateCheckInformation(applicationId, buildCheckInformation(updateRequest))
        } else {
          Future.successful(ApplicationUpdateSuccessful)
        }
      }

      def handleValidForm(form: EditApplicationForm) = {
        val updateRequest = UpdateApplicationRequest.from(form, application)

        for {
          _ <- updateApplication(updateRequest)
          _ <- updateCheckInformation(updateRequest)
        } yield Redirect(controllers.routes.Details.details(applicationId))
      }

      def handleInvalidForm(formWithErrors: Form[EditApplicationForm]) = errorView(application.id, formWithErrors, application)

      EditApplicationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
    }

  private def errorView(id: String,
                        form: Form[EditApplicationForm], application: Application)
                       (implicit request: ApplicationRequest[_]): Future[Result] =
    Future.successful(BadRequest(views.html.changeDetails(form, application)))
}
