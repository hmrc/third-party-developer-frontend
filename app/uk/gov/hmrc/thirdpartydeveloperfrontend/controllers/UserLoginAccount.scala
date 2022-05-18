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

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request, Result, Session => PlaySession}
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.models.DeviceSession
import uk.gov.hmrc.apiplatform.modules.mfa.service.MfaMandateService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.UserAuthenticationResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.MfaMandateDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import views.html._
import views.html.protectaccount._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

trait Auditing {
  val auditService: AuditService

  def audit(auditAction: AuditAction, data: Map[String, String])(implicit hc: HeaderCarrier): Future[AuditResult] = {
    auditService.audit(auditAction, data)
  }

  def audit(auditAction: AuditAction, developer: DeveloperSession)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    auditService.audit(auditAction, Map("developerEmail" -> developer.email, "developerFullName" -> developer.displayedName))
  }
}

@Singleton
class UserLoginAccount @Inject()(val auditService: AuditService,
                                 val errorHandler: ErrorHandler,
                                 val applicationService: ApplicationService,
                                 val subscriptionFieldsService: SubscriptionFieldsService,
                                 tpdService: ThirdPartyDeveloperConnector,
                                 val sessionService: SessionService,
                                 val thirdPartyDeveloperMfaConnector: ThirdPartyDeveloperMfaConnector,
                                 mcc: MessagesControllerComponents,
                                 val mfaMandateService: MfaMandateService,
                                 val cookieSigner : CookieSigner,
                                 signInView: SignInView,
                                 accountLockedView: AccountLockedView,
                                 logInAccessCodeView: LogInAccessCodeView,
                                 protectAccountNoAccessCodeView: ProtectAccountNoAccessCodeView,
                                 protectAccountNoAccessCodeCompleteView: ProtectAccountNoAccessCodeCompleteView,
                                 userDidNotAdd2SVView: UserDidNotAdd2SVView,
                                 add2SVView: Add2SVView
                                )
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends LoggedOutController(mcc) with Auditing with ApplicationLogger {

  import play.api.data._

  val loginForm: Form[LoginForm] = LoginForm.form
  val changePasswordForm: Form[ChangePasswordForm] = ChangePasswordForm.form

  def login: Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(signInView("Sign in", loginForm)))
  }

  def accountLocked: Action[AnyContent] = Action.async { implicit request =>
    for {
      _ <- extractSessionIdFromCookie(request)
        .map(sessionService.destroy)
        .getOrElse(successful(()))
    } yield Locked(accountLockedView())
  }

  
  def get2SVNotSetPage(): Action[AnyContent] = loggedInAction { implicit request =>
    successful(Ok(userDidNotAdd2SVView()))
  }

  def get2svRecommendationPage(): Action[AnyContent] = loggedInAction {
    implicit request => {
      for {
        showAdminMfaMandateMessage <- mfaMandateService.showAdminMfaMandatedMessage(request.userId)
        mfaMandateDetails = MfaMandateDetails(showAdminMfaMandateMessage, mfaMandateService.daysTillAdminMfaMandate.getOrElse(0))
      }  yield (Ok(add2SVView(mfaMandateDetails)))
    }
  }


  private def routeToLoginOr2SV(login: LoginForm,
                                userAuthenticationResponse: UserAuthenticationResponse,
                                playSession: PlaySession,
                                userId: UserId)(implicit request: Request[AnyContent]): Future[Result] = {

    // In each case retain the Play session so that 'access_uri' query param, if set, is used at the end of the 2SV reminder flow
    (userAuthenticationResponse.session, userAuthenticationResponse.accessCodeRequired) match {
      case (Some(session), false) if session.loggedInState.isLoggedIn => audit(LoginSucceeded, DeveloperSession.apply(session))
        if(userAuthenticationResponse.mfaEnabled) {
          successful(
            withSessionCookie(
              Redirect(routes.ManageApplications.manageApps(), SEE_OTHER).withSession(playSession),
              session.sessionId
            )
          )
        } else {
          successful(
            withSessionCookie(
              Redirect(routes.UserLoginAccount.get2svRecommendationPage(), SEE_OTHER).withSession(playSession),
              session.sessionId
            )
          )
        }

      case (Some(session), false) if session.loggedInState.isPartLoggedInEnablingMFA =>
        successful(
          withSessionCookie(
            Redirect(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.ProtectAccount.getProtectAccount().url).withSession(playSession),
            session.sessionId
          )
        )

      case (None, true) =>
        // TODO: delete device session cookie --- If Device cookie is going to be refreshed / recreated do we need to delete it?
        successful(
          Redirect(
            routes.UserLoginAccount.enterTotp(), SEE_OTHER
          ).withSession(
            playSession + ("emailAddress" -> login.emailaddress) + ("nonce" -> userAuthenticationResponse.nonce.get)
          )
        )



    }
  }

  def authenticate: Action[AnyContent] = Action.async { implicit request =>
    val requestForm = loginForm.bindFromRequest

    requestForm.fold(
      errors => successful(BadRequest(signInView("Sign in", errors))),
      login => {
        val deviceSessionId = request.cookies.get("DEVICE_SESS_ID").flatMap(x => decodeCookie(x.value)).map(UUID.fromString)
        for {
          (userAuthenticationResponse, userId) <- sessionService.authenticate(login.emailaddress, login.password, deviceSessionId)
          response <- routeToLoginOr2SV(login, userAuthenticationResponse, request.session, userId)
        } yield response
      } recover {
        case _: InvalidEmail =>
          logger.warn("Login failed due to invalid Email")
          audit(LoginFailedDueToInvalidEmail, Map("developerEmail" -> login.emailaddress))
          Unauthorized(signInView("Sign in", LoginForm.invalidCredentials(requestForm, login.emailaddress)))
        case _: InvalidCredentials =>
          logger.warn("Login failed due to invalid credentials")
          audit(LoginFailedDueToInvalidPassword, Map("developerEmail" -> login.emailaddress))
          Unauthorized(signInView("Sign in", LoginForm.invalidCredentials(requestForm, login.emailaddress)))
        case _: LockedAccount =>
          logger.warn("Login failed account locked")
          audit(LoginFailedDueToLockedAccount, Map("developerEmail" -> login.emailaddress))
          Locked(accountLockedView())
        case _: UnverifiedAccount =>
          logger.warn("Login failed unverified account")
          Forbidden(signInView("Sign in", LoginForm.accountUnverified(requestForm, login.emailaddress)))
            .withSession("email" -> login.emailaddress)
      }
    )
  }

  def enterTotp: Action[AnyContent] = Action.async { implicit request =>
      Future.successful(Ok(logInAccessCodeView(MfaAccessCodeForm.form)))
  }

  def authenticateTotp: Action[AnyContent] = Action.async { implicit request =>
    MfaAccessCodeForm.form.bindFromRequest.fold(
      errors => successful(BadRequest(logInAccessCodeView(errors))),
      validForm => {
        val email = request.session.get("emailAddress").get
        sessionService.authenticateTotp(email, validForm.accessCode, request.session.get("nonce").get) flatMap { session =>
          audit(LoginSucceeded, DeveloperSession.apply(session))
          //create device session if 7 days checkbox clicked (from form)  then create cookie
          if(validForm.rememberMe) {
            thirdPartyDeveloperMfaConnector.createDeviceSession(session.developer.userId) flatMap {
              case Some(deviceSession: DeviceSession) =>
                loginSucceeded(request).map(r => withSessionAndDeviceCookies(r, session.sessionId, deviceSession.deviceSessionId.toString))
              case _ => successful(InternalServerError(""))
            }
          }else {
            loginSucceeded(request).map(r => withSessionCookie(r, session.sessionId))
          }


        } recover {
          case _: InvalidCredentials =>
            logger.warn("Login failed due to invalid access code")
            audit(LoginFailedDueToInvalidAccessCode, Map("developerEmail" -> email))
            Unauthorized(logInAccessCodeView(MfaAccessCodeForm.form.fill(validForm).withError("accessCode", "You have entered an incorrect access code")))
        }
      }
    )
  }

  def get2SVHelpConfirmationPage(): Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(protectAccountNoAccessCodeView()))
  }

  def get2SVHelpCompletionPage(): Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(protectAccountNoAccessCodeCompleteView()))
  }

  def confirm2SVHelp(): Action[AnyContent] = loggedOutAction { implicit request =>
    import cats.data.OptionT
    import cats.implicits._

    val email = request.session.get("emailAddress").getOrElse("")

    def findName: Future[Option[String]] =
      (
        for {
          details   <- OptionT(tpdService.findUserId(email))
          developer <- OptionT(tpdService.fetchDeveloper(details.id))
        } yield s"${developer.firstName } ${developer.lastName}"
      )
      .value

    for {
      oName <- findName
      _     <- applicationService.request2SVRemoval(
                 name = oName.getOrElse("Unknown"),
                 email
               )
    } yield Ok(protectAccountNoAccessCodeCompleteView())
  }
}


final case class MfaAccessCodeForm(accessCode: String, rememberMe: Boolean)

object MfaAccessCodeForm {
  def form: Form[MfaAccessCodeForm] = Form(
    mapping(
      "accessCode" -> text.verifying(FormKeys.accessCodeInvalidKey, s => s.matches("^[0-9]{6}$")),
      "rememberMe" -> boolean

    )(MfaAccessCodeForm.apply)(MfaAccessCodeForm.unapply)
  )
}

