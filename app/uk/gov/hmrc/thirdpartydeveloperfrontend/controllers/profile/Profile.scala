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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.profile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.html._

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.UpdateRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationService, AuditService, SessionService}

@Singleton
class Profile @Inject() (
    val applicationService: ApplicationService,
    val auditService: AuditService,
    val sessionService: SessionService,
    val connector: ThirdPartyDeveloperConnector,
    val errorHandler: ErrorHandler,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    changeProfileViewTemplate: ChangeProfileView,
    profileView: ProfileView,
    profileUpdatedView: ProfileUpdatedView,
    changeProfilePasswordView: ChangeProfilePasswordView,
    passwordUpdatedView: PasswordUpdatedView,
    profileDeleteConfirmationView: ProfileDeleteConfirmationView,
    profileDeleteSubmittedView: ProfileDeleteSubmittedView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends LoggedInController(mcc) with PasswordChange {

  import ErrorFormBuilder.CommonGlobalErrorsSyntax
  import play.api.data._

  val profileForm: Form[ProfileForm]             = ProfileForm.form
  val passwordForm: Form[ChangePasswordForm]     = ChangePasswordForm.form
  val deleteProfileForm: Form[DeleteProfileForm] = DeleteProfileForm.form

  private def changeProfileView()(implicit req: UserRequest[_]) = {
    changeProfileViewTemplate(profileForm.fill(ProfileForm(
      req.userSession.developer.firstName,
      req.userSession.developer.lastName
    )))
  }

  def showProfile(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(profileView()))
  }

  def changeProfile(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(changeProfileView()))
  }

  def updateProfile(): Action[AnyContent] = loggedInAction { implicit request =>
    val requestForm = profileForm.bindFromRequest()
    requestForm.fold(
      formWithErrors => {
        Future.successful(BadRequest(changeProfileViewTemplate(formWithErrors.firstnameGlobal().lastnameGlobal())))
      },
      profile =>
        connector.updateProfile(request.userId, UpdateRequest(profile.firstName.trim, profile.lastName.trim)) map {
          _ =>
            {

              val updatedDeveloper = request.userSession.developer.copy(
                firstName = profile.firstName,
                lastName = profile.lastName
              )

              val updatedLoggedIn = request.userSession.copy(developer = updatedDeveloper)

              Ok(profileUpdatedView("profile updated", "Manage profile", "manage-profile", updatedLoggedIn))
            }
        }
    )
  }

  def showPasswordPage(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(changeProfilePasswordView(passwordForm)))
  }

  def updatePassword(): Action[AnyContent] = loggedInAction { implicit request =>
    processPasswordChange(request.userSession.developer.email, Ok(passwordUpdatedView("password changed", "Password changed", "change-password")), changeProfilePasswordView(_))
  }

  def requestDeletion(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(profileDeleteConfirmationView(DeleteProfileForm.form)))
  }

  def deleteAccount(): Action[AnyContent] = loggedInAction { implicit request =>
    val form = deleteProfileForm.bindFromRequest()

    form.fold(
      invalidForm => {
        Future.successful(BadRequest(profileDeleteConfirmationView(invalidForm)))
      },
      validForm => {
        validForm.confirmation match {
          case Some("true") => applicationService
              .requestDeveloperAccountDeletion(request.userSession.developer.userId, request.userSession.developer.displayedName, request.userSession.developer.email)
              .map(_ => Ok(profileDeleteSubmittedView()))

          case _ => Future.successful(Ok(changeProfileView()))
        }
      }
    )
  }
}
