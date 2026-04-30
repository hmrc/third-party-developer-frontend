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

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, ApplicationWithCollaborators, ValidatedApplicationName}
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommand, ApplicationCommands}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationDetailsSectionsController.ApplicationNameModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Conversions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.{appNameField, applicationNameAlreadyExistsKey, applicationNameInvalidKey}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseV2State
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{ProductionAndAdmin, SandboxOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import views.html._
import views.html.manageapplication.ChangeAppNameAndDescView

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

object RequestChangeOfApplicationNameController {
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
class RequestChangeOfApplicationNameController @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val clock: Clock,
    val changeAppNameAndDescView: ChangeAppNameAndDescView,
    changeDetailsView: ChangeDetailsView,
    requestChangeOfApplicationNameView: RequestChangeOfApplicationNameView,
    changeOfApplicationNameConfirmationView: ChangeOfApplicationNameConfirmationView,
    updatePrivacyPolicyLocationView: UpdatePrivacyPolicyLocationView,
    updateTermsAndConditionsLocationView: UpdateTermsAndConditionsLocationView,
    val fraudPreventionConfig: FraudPreventionConfig
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with FraudPreventionNavLinkHelper
    with WithUnsafeDefaultFormBinding
    with ClockNow {

  def canChangeProductionDetailsAndIsApprovedAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsDetails, ProductionAndAdmin)(applicationId)(fun)


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
