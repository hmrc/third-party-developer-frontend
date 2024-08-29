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

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import views.html._

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Session => PlaySession, _}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.forms.{MfaAccessCodeForm, SelectLoginMfaForm}
import uk.gov.hmrc.apiplatform.modules.mfa.utils.MfaDetailHelper
import uk.gov.hmrc.apiplatform.modules.mfa.utils.MfaDetailHelper.{getMfaDetailById, getMfaDetailByType, hasVerifiedSmsAndAuthApp}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.AuthAppLoginAccessCodeView
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.SmsLoginAccessCodeView
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.{RequestMfaRemovalCompleteView, RequestMfaRemovalView}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaType.{AUTHENTICATOR_APP, SMS}
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

trait Auditing {
  val auditService: AuditService

  def audit(auditAction: AuditAction, data: Map[String, String])(implicit hc: HeaderCarrier): Future[AuditResult] = {
    auditService.audit(auditAction, data)
  }

  def audit(auditAction: AuditAction, userSession: UserSession)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    auditService.audit(auditAction, Map("developerEmail" -> userSession.developer.email.text, "developerFullName" -> userSession.developer.displayedName))
  }
}

@Singleton
class UserLoginAccount @Inject() (
    val auditService: AuditService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val subscriptionFieldsService: SubscriptionFieldsService,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    val sessionService: SessionService,
    val thirdPartyDeveloperMfaConnector: ThirdPartyDeveloperMfaConnector,
    mcc: MessagesControllerComponents,
    val appsByTeamMember: AppsByTeamMemberService,
    val cookieSigner: CookieSigner,
    signInView: SignInView,
    accountLockedView: AccountLockedView,
    authAppLoginAccessCodeView: AuthAppLoginAccessCodeView,
    smsLoginAccessCodeView: SmsLoginAccessCodeView,
    selectLoginMfaView: SelectLoginMfaView,
    requestMfaRemovalView: RequestMfaRemovalView,
    requestMfaRemovalCompleteView: RequestMfaRemovalCompleteView,
    userDidNotAdd2SVView: UserDidNotAdd2SVView,
    add2SVView: Add2SVView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends LoggedOutController(mcc) with Auditing with ApplicationLogger {

  import play.api.data._

  val loginForm: Form[LoginForm]                   = LoginForm.form
  val changePasswordForm: Form[ChangePasswordForm] = ChangePasswordForm.form

  def login: Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(signInView("Sign in", loginForm)))
  }

  def accountLocked: Action[AnyContent] = Action.async { implicit request =>
    for {
      _ <- extractUserSessionIdFromCookie(request)
             .map(sessionService.destroy)
             .getOrElse(successful(()))
    } yield Locked(accountLockedView())
  }

  def get2SVNotSetPage(): Action[AnyContent] = loggedInAction { implicit request =>
    successful(Ok(userDidNotAdd2SVView()))
  }

  def get2svRecommendationPage(): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction {
    implicit request =>
      {
        for {
          isAdminOnProductionApp <- appsByTeamMember.fetchProductionSummariesByAdmin(request.userId).map(_.nonEmpty)
        } yield (Ok(add2SVView(isAdminOnProductionApp)))
      }
  }

  private def routeToLoginOr2SV(
      login: LoginForm,
      userAuthenticationResponse: UserAuthenticationResponse,
      playSession: PlaySession,
      userId: UserId
    )(implicit request: Request[AnyContent]
    ): Future[Result] = {

    // In each case retain the Play session so that 'access_uri' query param, if set, is used at the end of the 2SV reminder flow
    (userAuthenticationResponse.session, userAuthenticationResponse.accessCodeRequired) match {
      case (Some(session), false) if session.loggedInState.isLoggedIn =>
        audit(LoginSucceeded, session)
        // If "remember me" was ticked on a previous login, MFA will be enabled but the access code is not required
        if (userAuthenticationResponse.mfaEnabled) {
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
            Redirect(routes.UserLoginAccount.get2svRecommendationPage(), SEE_OTHER).withSession(playSession),
            session.sessionId
          )
        )

      case (None, true) =>
        // TODO: delete device session cookie --- If Device cookie is going to be refreshed / recreated do we need to delete it?
        thirdPartyDeveloperConnector.fetchDeveloper(userId) flatMap {
          case Some(developer: User) => handleMfaChoices(developer, playSession, login.emailaddress.toLaxEmail, userAuthenticationResponse.nonce.getOrElse(""))
          case None                  => throw new UserNotFound
        }

    }
  }

  private def handleAuthAppFlow(userId: UserId, authAppDetail: AuthenticatorAppMfaDetail, session: PlaySession) = {
    successful(
      Redirect(routes.UserLoginAccount.loginAccessCodePage(authAppDetail.id, AUTHENTICATOR_APP), SEE_OTHER).withSession(session + ("userId" -> userId.value.toString))
    )
  }

  private def handleSmsFlow(userId: UserId, smsMfaDetail: SmsMfaDetail, session: PlaySession)(implicit hc: HeaderCarrier) = {
    thirdPartyDeveloperMfaConnector.sendSms(userId, smsMfaDetail.id).map {
      case true =>
        Redirect(routes.UserLoginAccount.loginAccessCodePage(smsMfaDetail.id, SMS), SEE_OTHER)
          .withSession(session + ("userId" -> userId.value.toString))
          .flashing("mobileNumber" -> smsMfaDetail.mobileNumber)

      case false => InternalServerError("Failed to send SMS")
    }
  }

  private def handleMfaChoiceFlow(userId: UserId, authAppMfaId: MfaId, smsMfaId: MfaId, session: PlaySession) = {
    successful(Redirect(routes.UserLoginAccount.selectLoginMfaPage(authAppMfaId, smsMfaId), SEE_OTHER)
      .withSession(session + ("userId" -> userId.value.toString)))
  }

  private def handleMfaChoices(developer: User, playSession: PlaySession, emailAddress: LaxEmailAddress, nonce: String)(implicit hc: HeaderCarrier) = {
    val session: PlaySession = playSession + ("emailAddress" -> emailAddress.text) + ("nonce" -> nonce)

    (MfaDetailHelper.getAuthAppMfaVerified(developer.mfaDetails), MfaDetailHelper.getSmsMfaVerified(developer.mfaDetails)) match {
      case (None, None)                                                              => successful(InternalServerError("Access code required but mfa not set up"))
      case (Some(x: AuthenticatorAppMfaDetail), None)                                => handleAuthAppFlow(developer.userId, x, session)
      case (None, Some(x: SmsMfaDetail))                                             => handleSmsFlow(developer.userId, x, session)
      case (Some(authAppMfa: AuthenticatorAppMfaDetail), Some(smsMfa: SmsMfaDetail)) =>
        handleMfaChoiceFlow(developer.userId, authAppMfa.id, smsMfa.id, session)
    }
  }

  def selectLoginMfaPage(authAppMfaId: MfaId, smsMfaId: MfaId): Action[AnyContent] = Action.async { implicit request =>
    successful(Ok(selectLoginMfaView(SelectLoginMfaForm.form, authAppMfaId, smsMfaId)))
  }

  def selectLoginMfaAction(): Action[AnyContent] = Action.async { implicit request =>
    def handleSelectedMfa(userId: UserId, mfaDetail: MfaDetail) = {
      mfaDetail match {
        case x: AuthenticatorAppMfaDetail => handleAuthAppFlow(userId, x, request.session)
        case x: SmsMfaDetail              => handleSmsFlow(userId, x, request.session)
      }
    }

    def handleMfaLogin(form: SelectLoginMfaForm) = {
      val userId = UserId(UUID.fromString(request.session.get("userId").get))

      thirdPartyDeveloperConnector.fetchDeveloper(userId) flatMap {
        case Some(developer: User) =>
          getMfaDetailById(MfaId(UUID.fromString(form.mfaId)), developer.mfaDetails)
            .map(mfaDetail => handleSelectedMfa(userId, mfaDetail))
            .getOrElse(successful(InternalServerError("Access code required but mfa not set up")))
        case None                  => successful(NotFound("User not found"))
      }
    }

    SelectLoginMfaForm.form.bindFromRequest().fold(
      hasErrors => successful(BadRequest(s"Error while selecting mfaId: ${hasErrors.errors.toString()}")),
      form => handleMfaLogin(form)
    )
  }

  def authenticate: Action[AnyContent] = Action.async { implicit request =>
    val requestForm = loginForm.bindFromRequest()

    requestForm.fold(
      errors => successful(BadRequest(signInView("Sign in", errors))),
      loginForm =>
        {
          val deviceSessionId = extractDeviceSessionIdFromCookie(request)
          for {
            (userAuthenticationResponse, userId) <- sessionService.authenticate(loginForm.emailaddress.toLaxEmail, loginForm.password, deviceSessionId)
            response                             <- routeToLoginOr2SV(loginForm, userAuthenticationResponse, request.session, userId)
          } yield response
        } recoverWith {
          case _: InvalidEmail       =>
            logger.warn("Login failed due to invalid Email")
            audit(LoginFailedDueToInvalidEmail, Map("developerEmail" -> loginForm.emailaddress))
            successful(Unauthorized(signInView("Sign in", LoginForm.invalidCredentials(requestForm, loginForm.emailaddress))))
          case _: InvalidCredentials =>
            logger.warn("Login failed due to invalid credentials")
            audit(LoginFailedDueToInvalidPassword, Map("developerEmail" -> loginForm.emailaddress))
            successful(Unauthorized(signInView("Sign in", LoginForm.invalidCredentials(requestForm, loginForm.emailaddress))))
          case _: LockedAccount      =>
            logger.warn("Login failed account locked")
            audit(LoginFailedDueToLockedAccount, Map("developerEmail" -> loginForm.emailaddress))
            successful(Locked(accountLockedView()))
          case _: UnverifiedAccount  =>
            logger.warn("Login failed unverified account")
            successful(Forbidden(signInView("Sign in", LoginForm.accountUnverified(requestForm, loginForm.emailaddress)))
              .withSession("email" -> loginForm.emailaddress))
          case _: UserNotFound       =>
            logger.warn("Login failed due to user not found")
            errorHandler.internalServerErrorTemplate.map(InternalServerError(_))
          case _: MatchError         =>
            logger.warn("Inconsistent response from server")
            errorHandler.internalServerErrorTemplate.map(InternalServerError(_))
        }
    )
  }

  def tryAnotherOption(): Action[AnyContent] = Action.async { implicit request =>
    val userId = UserId(UUID.fromString(request.session.get("userId").get))

    thirdPartyDeveloperConnector.fetchDeveloper(userId)
      .flatMap {
        case Some(developer: User) =>
          {}
          if (hasVerifiedSmsAndAuthApp(developer.mfaDetails)) {
            val authAppMfaId = getMfaDetailByType(MfaType.AUTHENTICATOR_APP, developer.mfaDetails)
            val smsMfaId     = getMfaDetailByType(MfaType.SMS, developer.mfaDetails)
            successful(Ok(selectLoginMfaView(SelectLoginMfaForm.form, authAppMfaId.id, smsMfaId.id)))
          } else {
            logger.warn("Inconsistent state of user mfa")
            errorHandler.internalServerErrorTemplate.map(InternalServerError(_))
          }

        case None => successful(NotFound("User not found"))
      }
  }

  def loginAccessCodePage(mfaId: MfaId, mfaType: MfaType): Action[AnyContent] = Action.async { implicit request =>
    val userId = UserId(UUID.fromString(request.session.get("userId").get))

    def handleMfaType(userHasMultipleMfa: Boolean) = {
      mfaType match {
        case AUTHENTICATOR_APP => Ok(authAppLoginAccessCodeView(MfaAccessCodeForm.form, mfaId, mfaType, userHasMultipleMfa))
        case SMS               => Ok(smsLoginAccessCodeView(MfaAccessCodeForm.form, mfaId, mfaType, userHasMultipleMfa))
      }
    }

    thirdPartyDeveloperConnector.fetchDeveloper(userId).map {
      case Some(developer: User) => handleMfaType(hasMultipleMfaMethods(developer))
      case None                  => handleMfaType(false)
    }
  }

  private def hasMultipleMfaMethods(developer: User): Boolean = hasVerifiedSmsAndAuthApp(developer.mfaDetails)

  def authenticateAccessCode(mfaId: MfaId, mfaType: MfaType, userHasMultipleMfa: Boolean): Action[AnyContent] = Action.async { implicit request =>
    val email: LaxEmailAddress = request.session.get("emailAddress").get.toLaxEmail

    def handleMfaSetupReminder(session: UserSession) = {
      val verifiedMfaDetailsOfOtherTypes = session.developer.mfaDetails.filter(_.verified).filterNot(_.mfaType == mfaType)

      (verifiedMfaDetailsOfOtherTypes.isEmpty, mfaType) match {
        case (true, AUTHENTICATOR_APP) => successful(Redirect(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.MfaController.smsSetupReminderPage()))
        case (true, SMS)               => successful(Redirect(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.MfaController.authAppSetupReminderPage()))
        case _                         => loginSucceeded(request)
      }
    }

    def handleRememberMe(form: MfaAccessCodeForm, session: UserSession): Future[Result] = {

      if (form.rememberMe) {
        thirdPartyDeveloperMfaConnector.createDeviceSession(session.developer.userId) flatMap {
          case Some(deviceSession: DeviceSession) => handleMfaSetupReminder(session).map(withSessionAndDeviceCookies(_, session.sessionId, deviceSession.deviceSessionId.toString))
          case _                                  => successful(InternalServerError(""))
        }
      } else handleMfaSetupReminder(session).map(withSessionCookie(_, session.sessionId))
    }

    def handleAuthentication(email: LaxEmailAddress, form: MfaAccessCodeForm, mfaType: MfaType, userHasMultipleMfa: Boolean) = {
      (for {
        session <- sessionService.authenticateAccessCode(email, form.accessCode, request.session.get("nonce").get, mfaId)
        _       <- audit(LoginSucceeded, session)
        result  <- handleRememberMe(form, session)
      } yield result)
        .recover {
          case _: InvalidCredentials =>
            logger.warn("Login failed due to invalid access code")
            audit(LoginFailedDueToInvalidAccessCode, Map("developerEmail" -> email.text))
            handleAccessCodeError(form, mfaId, mfaType, userHasMultipleMfa)
        }
    }

    def handleFormWithErrors(formWithErrors: Form[MfaAccessCodeForm], mfaId: MfaId, mfaType: MfaType, userHasMultipleMfa: Boolean) = mfaType match {
      case AUTHENTICATOR_APP => BadRequest(authAppLoginAccessCodeView(formWithErrors, mfaId, mfaType, userHasMultipleMfa))
      case SMS               => BadRequest(smsLoginAccessCodeView(formWithErrors, mfaId, mfaType, userHasMultipleMfa))
    }

    def handleAccessCodeError(form: MfaAccessCodeForm, mfaId: MfaId, mfaType: MfaType, userHasMultipleMfa: Boolean) = {
      mfaType match {
        case AUTHENTICATOR_APP => Unauthorized(authAppLoginAccessCodeView(
            MfaAccessCodeForm.form.fill(form).withError("accessCode", "You have entered an incorrect access code"),
            mfaId,
            mfaType,
            userHasMultipleMfa
          ))
        case SMS               => Unauthorized(smsLoginAccessCodeView(
            MfaAccessCodeForm.form.fill(form).withError("accessCode", "You have entered an incorrect access code"),
            mfaId,
            mfaType,
            userHasMultipleMfa
          ))
      }
    }

    MfaAccessCodeForm.form.bindFromRequest().fold(
      errors => successful(handleFormWithErrors(errors, mfaId, mfaType, userHasMultipleMfa)),
      validForm => handleAuthentication(email, validForm, mfaType, userHasMultipleMfa)
    )
  }

  def get2SVHelpConfirmationPage(): Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(requestMfaRemovalView()))
  }

  def get2SVHelpCompletionPage(): Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(requestMfaRemovalCompleteView()))
  }

  // TODO - this whole piece of code looks like it should be handling the lack of an emailAddress much better - perhaps dropping through all the for comp to return a BadRequest
  def confirm2SVHelp(): Action[AnyContent] = loggedOutAction { implicit request =>
    import cats.data.OptionT
    import cats.implicits._

    val email = request.session.get("emailAddress").getOrElse("").toLaxEmail

    def findDeveloper = {
      (for {
        details   <- OptionT(thirdPartyDeveloperConnector.findUserId(email))
        developer <- OptionT(thirdPartyDeveloperConnector.fetchDeveloper(details.id))
      } yield developer).value
    }

    def getFullName(developer: Option[User]) = developer.map(d => s"${d.firstName} ${d.lastName}").getOrElse("Unknown")

    for {
      developer <- findDeveloper
      userId     = developer.map(d => d.userId)
      fullName   = getFullName(developer)
      _         <- applicationService.request2SVRemoval(
                     userId,
                     name = fullName,
                     email
                   )
    } yield Ok(requestMfaRemovalCompleteView())
  }
}
