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

package uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.forms._
import uk.gov.hmrc.apiplatform.modules.mfa.service.{MfaResponse, MfaService}
import uk.gov.hmrc.apiplatform.modules.mfa.utils.MfaDetailHelper._
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp._
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms._
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.{RemoveMfaCompletedView, SecurityPreferencesView, SelectMfaView}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaType.{AUTHENTICATOR_APP, SMS}
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{MfaId, MfaType}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.LoggedInState
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto.UpdateLoggedInStateRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Conversions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{FormKeys, LoggedInController}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.mfa.MfaAction
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.mfa.MfaAction.{CREATE, REMOVE}
import uk.gov.hmrc.thirdpartydeveloperfrontend.qr.{OtpAuthUri, QRCode}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService

@Singleton
class MfaController @Inject() (
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    val thirdPartyDeveloperMfaConnector: ThirdPartyDeveloperMfaConnector,
    val otpAuthUri: OtpAuthUri,
    val mfaService: MfaService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val errorHandler: ErrorHandler,
    val cookieSigner: CookieSigner,
    val securityPreferencesView: SecurityPreferencesView,
    authAppStartView: AuthAppStartView,
    authAppAccessCodeView: AuthAppAccessCodeView,
    qrCodeView: QrCodeView,
    authAppSetupCompletedView: AuthAppSetupCompletedView,
    nameChangeView: NameChangeView,
    mobileNumberView: MobileNumberView,
    smsAccessCodeView: SmsAccessCodeView,
    smsSetupCompletedView: SmsSetupCompletedView,
    smsSetupSkippedView: SmsSetupSkippedView,
    smsSetupReminderView: SmsSetupReminderView,
    authAppSetupSkippedView: AuthAppSetupSkippedView,
    authAppSetupReminderView: AuthAppSetupReminderView,
    selectMfaView: SelectMfaView,
    removeMfaCompletedView: RemoveMfaCompletedView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends LoggedInController(mcc)
    with WithUnsafeDefaultFormBinding {
  val qrCode: QRCode = QRCode(scale = 4)

  def securityPreferences: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).flatMap {
      case Some(developer: User) => successful(Ok(securityPreferencesView(developer.mfaDetails.filter(_.verified))))
      case None                  => internalServerErrorTemplate("Unable to obtain User information")
    }
  }

  def selectMfaPage(mfaId: Option[MfaId] = None, mfaAction: MfaAction): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    successful(Ok(selectMfaView(SelectMfaForm.form, mfaAction, mfaId)))
  }

  def selectMfaAction(mfaId: Option[MfaId] = None, mfaAction: MfaAction): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    SelectMfaForm.form.bindFromRequest().fold(
      form => successful(BadRequest(selectMfaView(form, mfaAction, mfaId))),
      form => {
        mfaAction match {
          case CREATE => setupSelectedMfa(form.mfaType)
          case REMOVE => removeSelectedMfa(request.userId, form.mfaType, mfaId)
        }
      }
    )
  }

  private def setupSelectedMfa(mfaType: String): Future[Result] = {
    MfaType.unsafeApply(mfaType) match {
      case SMS               => successful(Redirect(routes.MfaController.setupSms()))
      case AUTHENTICATOR_APP => successful(Redirect(routes.MfaController.authAppStart()))
    }
  }

  private def removeSelectedMfa(userId: UserId, mfaTypeForAuthentication: String, mfaIdForRemoval: Option[MfaId])(implicit request: Request[_], hc: HeaderCarrier): Future[Result] = {

    def authenticateToRemoveMfa(mfaType: MfaType, mfaIdForAuthentication: MfaId): Future[Result] = {
      mfaType match {
        case SMS               =>
          thirdPartyDeveloperMfaConnector.sendSms(userId, mfaIdForAuthentication) flatMap {
            case true  => successful(Redirect(routes.MfaController.smsAccessCodePage(mfaIdForAuthentication, MfaAction.REMOVE, mfaIdForRemoval)))
            case false => internalServerErrorTemplate("Failed to send SMS")
          }
        case AUTHENTICATOR_APP => successful(Redirect(routes.MfaController.authAppAccessCodePage(mfaIdForAuthentication, MfaAction.REMOVE, mfaIdForRemoval)))
      }
    }

    thirdPartyDeveloperConnector.fetchDeveloper(userId) flatMap {
      case None                  => internalServerErrorTemplate("Unable to obtain user information")
      case Some(developer: User) =>
        val mfaType                = MfaType.unsafeApply(mfaTypeForAuthentication)
        val mfaIdForAuthentication = getMfaDetailByType(mfaType, developer.mfaDetails).id
        authenticateToRemoveMfa(mfaType, mfaIdForAuthentication)
    }
  }

  def authAppStart: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    successful(Ok(authAppStartView()))
  }

  def setupAuthApp: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperMfaConnector.createMfaAuthApp(request.userId).map(registerAuthAppResponse => {
      val uri   = otpAuthUri(registerAuthAppResponse.secret.toLowerCase, "HMRC Developer Hub", request.userSession.developer.email.text)
      val qrImg = qrCode.generateDataImageBase64(uri.toString)
      Ok(qrCodeView(registerAuthAppResponse.secret.toLowerCase().grouped(4).mkString(" "), qrImg, registerAuthAppResponse.mfaId))
    })
  }

  def authAppAccessCodePage(mfaId: MfaId, mfaAction: MfaAction, mfaIdForRemoval: Option[MfaId]): Action[AnyContent] =
    atLeastPartLoggedInEnablingMfaAction { implicit request =>
      successful(
        Ok(authAppAccessCodeView(MfaAccessCodeForm.form, mfaId, mfaAction, mfaIdForRemoval))
      )
    }

  def authAppAccessCodeAction(mfaId: MfaId, mfaAction: MfaAction, mfaIdForRemoval: Option[MfaId]): Action[AnyContent] =
    atLeastPartLoggedInEnablingMfaAction { implicit request =>
      def logonAndComplete(): Result = {
        thirdPartyDeveloperConnector.updateSessionLoggedInState(request.sessionId, UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN))
        Redirect(routes.MfaController.nameChangePage(mfaId))
      }

      def invalidCode(form: MfaAccessCodeForm): Result = {
        val mfaAccessCodeForm = MfaAccessCodeForm
          .form
          .fill(form)
          .withError(key = "accessCode", message = "You have entered an incorrect access code")

        BadRequest(authAppAccessCodeView(mfaAccessCodeForm, mfaId, mfaAction, mfaIdForRemoval))
      }

      MfaAccessCodeForm.form.bindFromRequest().fold(
        form => successful(BadRequest(authAppAccessCodeView(form, mfaId, mfaAction, mfaIdForRemoval))),
        form =>
          mfaAction match {
            case REMOVE => handleRemoveMfa(request.userId, form.accessCode, mfaId, mfaIdForRemoval)
            case CREATE => for {
                mfaResponse <- mfaService.enableMfa(request.userId, mfaId, form.accessCode)
                result       = if (mfaResponse.totpVerified) logonAndComplete() else invalidCode(form)
              } yield result
          }
      )
    }

  def nameChangePage(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    successful(Ok(nameChangeView(MfaNameChangeForm.form, mfaId)))
  }

  def nameChangeAction(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    MfaNameChangeForm.form.bindFromRequest().fold(
      form => successful(BadRequest(nameChangeView(form, mfaId))),
      form => {
        thirdPartyDeveloperMfaConnector.changeName(request.userId, mfaId, form.name).flatMap {
          case true  => successful(Redirect(routes.MfaController.authAppSetupCompletedPage()))
          case false => internalServerErrorTemplate("Failed to change MFA name")
        }
      }
    )
  }

  def authAppSetupSkippedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    successful(Ok(authAppSetupSkippedView()))
  }

  def authAppSetupReminderPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    successful(Ok(authAppSetupReminderView()))
  }

  def authAppSetupCompletedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).flatMap {
      case Some(developer: User) => successful(Ok(authAppSetupCompletedView(!isSmsMfaVerified(developer.mfaDetails))))
      case None                  => internalServerErrorTemplate("Unable to obtain user information")
    }
  }

  def smsSetupSkippedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    successful(Ok(smsSetupSkippedView()))
  }

  def smsSetupReminderPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    successful(Ok(smsSetupReminderView()))
  }

  def setupSms: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    successful(Ok(mobileNumberView(MobileNumberForm.form)))
  }

  def setupSmsAction: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    MobileNumberForm.form.bindFromRequest().fold(
      form => successful(BadRequest(mobileNumberView(form))),
      form =>
        thirdPartyDeveloperMfaConnector.createMfaSms(request.userId, form.mobileNumber)
          .map {
            case Some(response) =>
              Redirect(routes.MfaController.smsAccessCodePage(response.mfaId, MfaAction.CREATE, None))
                .flashing("mobileNumber" -> response.mobileNumber)
            case None           =>
              val errorForm = MobileNumberForm.form
                .fill(form)
                .withError(key = "mobileNumber", message = "It cannot be used for access codes")
              BadRequest(mobileNumberView(errorForm))
          }
    )
  }

  def smsAccessCodePage(mfaId: MfaId, mfaAction: MfaAction, mfaIdForRemoval: Option[MfaId]): Action[AnyContent] =
    atLeastPartLoggedInEnablingMfaAction { implicit request =>
      successful(Ok(smsAccessCodeView(SmsAccessCodeForm.form, mfaId, mfaAction, mfaIdForRemoval)))
    }

  def smsAccessCodeAction(mfaId: MfaId, mfaAction: MfaAction, mfaIdForRemoval: Option[MfaId]): Action[AnyContent] =
    atLeastPartLoggedInEnablingMfaAction { implicit request =>
      def logonAndComplete(): Result = {
        thirdPartyDeveloperConnector.updateSessionLoggedInState(request.sessionId, UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN))
        Redirect(routes.MfaController.smsSetupCompletedPage())
      }

      def invalidSmsCode(form: SmsAccessCodeForm): Result = {
        val smsForm = SmsAccessCodeForm.form.fill(form).withError(key = "accessCode", message = FormKeys.accessCodeErrorKey)
        BadRequest(smsAccessCodeView(smsForm, mfaId, mfaAction, mfaIdForRemoval))
      }

      SmsAccessCodeForm.form.bindFromRequest().fold(
        form => successful(BadRequest(smsAccessCodeView(form, mfaId, mfaAction, mfaIdForRemoval))),
        form =>
          mfaAction match {
            case CREATE => thirdPartyDeveloperMfaConnector.verifyMfa(request.userId, mfaId, form.accessCode) map {
                case true  => logonAndComplete()
                case false => invalidSmsCode(form)
              }
            case REMOVE => handleRemoveMfa(request.userId, form.accessCode, mfaId, mfaIdForRemoval)
          }
      )
    }

  def smsSetupCompletedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).flatMap {
      case Some(developer: User) => successful(Ok(smsSetupCompletedView(!isAuthAppMfaVerified(developer.mfaDetails))))
      case None                  => internalServerErrorTemplate("Unable to obtain User information")
    }
  }

  def removeMfa(mfaId: MfaId, mfaType: MfaType): Action[AnyContent] = loggedInAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).flatMap {
      case Some(developer: User) =>
        (isMfaDetailVerified(mfaId, developer.mfaDetails), hasVerifiedSmsAndAuthApp(developer.mfaDetails)) match {
          case (true, false) => removeMfaUserWithOneMfaMethod(mfaId, mfaType, request.userId)
          case (true, true)  => successful(Redirect(routes.MfaController.selectMfaPage(Some(mfaId), MfaAction.REMOVE)))
          case (_, _)        => internalServerErrorTemplate("MFA setup not valid")
        }
      case None                  => internalServerErrorTemplate("Unable to obtain User information")
    }
  }

  def removeMfaCompletedPage(): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    successful(Ok(removeMfaCompletedView()))
  }

  private def removeMfaUserWithOneMfaMethod(mfaId: MfaId, mfaType: MfaType, userId: UserId)(implicit hc: HeaderCarrier, request: Request[_]): Future[Result] = {
    mfaType match {
      case AUTHENTICATOR_APP => successful(Redirect(routes.MfaController.authAppAccessCodePage(mfaId, MfaAction.REMOVE, Some(mfaId))))
      case SMS               =>
        thirdPartyDeveloperMfaConnector.sendSms(userId, mfaId).flatMap {
          case true  => successful(Redirect(routes.MfaController.smsAccessCodePage(mfaId, MfaAction.REMOVE, Some(mfaId))))
          case false => internalServerErrorTemplate("Failed to send SMS")
        }
    }
  }

  private def handleRemoveMfa(
      userId: UserId,
      accessCode: String,
      mfaIdToVerify: MfaId,
      mfaIdForRemoval: Option[MfaId]
    )(implicit request: Request[_]
    ): Future[Result] = {
    mfaIdForRemoval match {
      case Some(mfaId) =>
        mfaService.removeMfaById(userId, mfaIdToVerify, accessCode, mfaId).flatMap {
          case MfaResponse(true)  => successful(Redirect(routes.MfaController.removeMfaCompletedPage()))
          case MfaResponse(false) => internalServerErrorTemplate("Unable to verify access code")
        }
      case None        => internalServerErrorTemplate("Unable to find Mfa to remove")
    }
  }

  private def internalServerErrorTemplate(errorMessage: String)(implicit request: Request[_]): Future[Result] = {
    errorHandler.standardErrorTemplate(errorMessage, errorMessage, errorMessage).map(InternalServerError(_))
  }
}
