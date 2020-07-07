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
import domain.{EmailAlreadyInUse, RegistrationSuccessful}
import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, MessagesControllerComponents, Request}
import service.SessionService
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException}
import views.html.{AccountVerifiedView, ExpiredVerificationLinkView, RegistrationView, SignInView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Registration @Inject()(override val sessionService: SessionService,
                             val connector: ThirdPartyDeveloperConnector,
                             val errorHandler: ErrorHandler,
                             mcc: MessagesControllerComponents,
                             val cookieSigner : CookieSigner,
                             registrationView: RegistrationView,
                             signInView: SignInView,
                             accountVerifiedView: AccountVerifiedView,
                             expiredVerificationLinkView: ExpiredVerificationLinkView
                             )
                            (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends LoggedOutController(mcc) {

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
            domain.Registration(userData.firstName.trim, userData.lastName.trim, userData.emailaddress, userData.password, userData.organisation)
          connector.register(registration).map {
            case RegistrationSuccessful => Redirect(controllers.routes.Registration.confirmation()).addingToSession("email" -> userData.emailaddress)
            case EmailAlreadyInUse => BadRequest(registrationView(requestForm.emailAddressAlreadyInUse))
          }
        }
      )
  }

  def resendVerification = Action.async {
    implicit request =>
      request.session.get("email").fold(Future.successful(BadRequest(signInView("Sign in", LoginForm.form)))) { email =>
        connector.resendVerificationEmail(email) map {
          case status if status >= 200 && status < 300 => Redirect(controllers.routes.Registration.confirmation())
          case _ => NotFound(errorHandler.notFoundTemplate).removingFromSession("email")
        } recover {
          case _: NotFoundException => NotFound(errorHandler.notFoundTemplate).removingFromSession("email")
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
    } yield Ok(accountVerifiedView)) recover {
      case _: BadRequestException => BadRequest(expiredVerificationLinkView)
    }
  }

  def confirmation = Action.async {
    implicit request =>
      Future.successful(Ok(views.html.confirmation()))
  }

  def resendConfirmation = Action.async {
    implicit request =>
      Future.successful(Ok(views.html.resendConfirmation()))
  }
}
