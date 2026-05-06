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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.manageapplication

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Conversions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.appNameField
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{ApplicationController, ApplicationRequest, ChangeOfPrivacyPolicyLocationForm, routes}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.ProductionAndAdmin
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import views.html.UpdatePrivacyPolicyLocationView

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpdatePrivacyPolicyLocationController @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    updatePrivacyPolicyLocationView: UpdatePrivacyPolicyLocationView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with WithUnsafeDefaultFormBinding {

  private def canChangeProductionDetailsAndIsApprovedAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
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
          Redirect(routes.MainApplicationDetailsController.applicationDetails(applicationId))
        )
      }
    }

    def handleInvalidForm(formWithErrors: Form[ChangeOfPrivacyPolicyLocationForm]): Future[Result] = {
      Future.successful(BadRequest(updatePrivacyPolicyLocationView(formWithErrors, applicationId)))
    }

    ChangeOfPrivacyPolicyLocationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }
}
