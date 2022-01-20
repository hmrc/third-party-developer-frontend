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

package controllers

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import domain.models.developers.{EmailAlreadyInUse, Registration => RegistrationModel, RegistrationSuccessful}
import javax.inject.{Inject, Singleton}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{MessagesControllerComponents, Request}
import service.SessionService
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import views.html._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import uk.gov.hmrc.modules.common.services.ApplicationLogger

@Singleton
class Registration @Inject()(override val sessionService: SessionService,
                             val connector: ThirdPartyDeveloperConnector,
                             val errorHandler: ErrorHandler,
                             mcc: MessagesControllerComponents,
                             val cookieSigner : CookieSigner,
                             registrationView: RegistrationView,
                             signInView: SignInView,
                             accountVerifiedView: AccountVerifiedView,
                             expiredVerificationLinkView: ExpiredVerificationLinkView,
                             confirmationView: ConfirmationView,
                             resendConfirmationView: ResendConfirmationView
                             )
                            (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends LoggedOutController(mcc) with ApplicationLogger {

  import ErrorFormBuilder.GlobalError
  import play.api.data._

  val regForm: Form[RegisterForm] = RegistrationForm.form

  def registration() = loggedOutAction { implicit request =>
    Future.successful(Ok(registrationView(regForm)))
  }

  def register() = Action.async {
    implicit request =>
      val requestForm: Form[RegisterForm] = regForm.bindFromRequest
      requestForm.fold(
        formWithErrors => {
          Future.successful(BadRequest(registrationView(
            formWithErrors.firstnameGlobal().lastnameGlobal().emailaddressGlobal().passwordGlobal().passwordNoMatchField()
          )))
        },
        userData => {
          val registration =
            RegistrationModel(userData.firstName.trim, userData.lastName.trim, userData.emailaddress, userData.password, userData.organisation)
          connector.register(registration).map {
            case RegistrationSuccessful => Redirect(routes.Registration.confirmation()).addingToSession("email" -> userData.emailaddress)
            case EmailAlreadyInUse => BadRequest(registrationView(requestForm.emailAddressAlreadyInUse))
          }
        }
      )
  }

  def resendVerification = Action.async {
    implicit request =>
      request.session.get("email").fold(Future.successful(BadRequest(signInView("Sign in", LoginForm.form)))) { email =>
        connector.resendVerificationEmail(email)
        .map(_ => Redirect(routes.Registration.confirmation()))
        .recover {
          case NonFatal(e) => {
            logger.warn(s"resendVerification failed with ${e.getMessage}")
            NotFound(errorHandler.notFoundTemplate).removingFromSession("email")
          }
        }
      }
  }

  def ensureLoggedOut(implicit request: Request[_], hc: HeaderCarrier) = {
    extractSessionIdFromCookie(request)
      .map(sessionService.destroy)
      .getOrElse(Future.successful(()))
  }

  def verify(code: String) = Action.async { implicit request =>
    (for {
      _ <- ensureLoggedOut
      _ <- connector.verify(code)
    } yield Ok(accountVerifiedView())) recover {
      case _: BadRequestException => BadRequest(expiredVerificationLinkView())
    }
  }

  def confirmation = Action.async {
    implicit request =>
      Future.successful(Ok(confirmationView()))
  }

  def resendConfirmation = Action.async {
    implicit request =>
      Future.successful(Ok(resendConfirmationView()))
  }
}
