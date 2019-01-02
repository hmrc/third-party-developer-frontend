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
import domain.{Developer, UpdateProfileRequest}
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.stackc.RequestWithAttributes
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import service.{ApplicationService, AuditService, SessionService}
import views.html._

import scala.concurrent.Future

@Singleton
class Profile @Inject()(applicationService: ApplicationService,
                        val auditService: AuditService,
                        val sessionService: SessionService,
                        val connector: ThirdPartyDeveloperConnector,
                        val errorHandler: ErrorHandler,
                        implicit val appConfig: ApplicationConfig)
  extends LoggedInController with PasswordChange {

  import ErrorFormBuilder.GlobalError
  import play.api.data._

  val profileForm: Form[ProfileForm] = ProfileForm.form
  val passwordForm: Form[ChangePasswordForm] = ChangePasswordForm.form
  val deleteProfileForm: Form[DeleteProfileForm] = DeleteProfileForm.form

  private def changeProfileView(user: Developer)(implicit req: RequestWithAttributes[_]) = {
    views.html.changeProfile(profileForm.fill(ProfileForm(user.firstName, user.lastName, user.organisation)))
  }

  def showProfile() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.profile()))
  }

  def changeProfile() = loggedInAction { implicit request =>
    Future.successful(Ok(changeProfileView(loggedIn)))
  }

  def updateProfile() = loggedInAction { implicit request =>
    val requestForm = profileForm.bindFromRequest
    requestForm.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.changeProfile(formWithErrors.firstnameGlobal().lastnameGlobal())))
      },
      profile => connector.updateProfile(loggedIn.email, UpdateProfileRequest(profile.firstName.trim, profile.lastName.trim, profile.organisation)) map {
        _ =>
          Ok(profileUpdated("profile updated", "Manage profile", "manage-profile")(request,
            Developer(loggedIn.email, profile.firstName, profile.lastName, profile.organisation), applicationMessages, appConfig))
      }
    )
  }

  def showPasswordPage() = loggedInAction { implicit request =>
    Future.successful(Ok(changeProfilePassword(passwordForm)))
  }

  def updatePassword() = loggedInAction { implicit request =>
    processPasswordChange(loggedIn.email,
      Ok(passwordUpdated("password changed", "Password changed", "change-password")),
      changeProfilePassword(_))
  }

  def requestDeletion() = loggedInAction { implicit request =>
    Future.successful(Ok(profileDeleteConfirmation(DeleteProfileForm.form)))
  }

  def deleteAccount() = loggedInAction { implicit request =>
    val form = deleteProfileForm.bindFromRequest

    form.fold(
      invalidForm => {
        Future.successful(BadRequest(profileDeleteConfirmation(invalidForm)))
      },
      validForm => {
        validForm.confirmation match {
          case Some("true") =>
            applicationService.requestDeveloperAccountDeletion(loggedIn.displayedName, loggedIn.email).map(_ => Ok(profileDeleteSubmitted()))

          case _ => Future.successful(Ok(changeProfileView(loggedIn)))
        }
      }
    )
  }
}
