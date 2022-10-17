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

package uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request, Result}
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.models.{MfaAction, MfaId, MfaType}
import uk.gov.hmrc.apiplatform.modules.mfa.service.{MfaResponse, MfaService}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.{RemoveMfaCompletedView, SecurityPreferencesView, SelectMfaView}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.{AuthAppAccessCodeView, AuthAppSetupCompletedView, AuthAppStartView, NameChangeView, QrCodeView}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.LoggedInController
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.UpdateLoggedInStateRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, DeveloperSession, LoggedInState, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.qr.{OtpAuthUri, QRCode}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes._
import uk.gov.hmrc.apiplatform.modules.mfa.forms.{MfaAccessCodeForm, MfaNameChangeForm, MobileNumberForm, SelectMfaForm, SmsAccessCodeForm}
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaAction.{CREATE, REMOVE}
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaType.{AUTHENTICATOR_APP, SMS}
import uk.gov.hmrc.apiplatform.modules.mfa.utils.MfaDetailHelper
import uk.gov.hmrc.apiplatform.modules.mfa.utils.MfaDetailHelper.{getMfaDetailByType, hasVerifiedSmsAndAuthApp, isAuthAppMfaVerified, isSmsMfaVerified}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.{MobileNumberView, SmsAccessCodeView, SmsSetupCompletedView}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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
  selectMfaView: SelectMfaView,
  removeMfaCompletedView: RemoveMfaCompletedView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig)
    extends LoggedInController(mcc)
    with WithUnsafeDefaultFormBinding {
  val qrCode: QRCode = QRCode(scale = 4)

  def securityPreferences: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).map {
      case Some(developer: Developer) => Ok(securityPreferencesView(developer.mfaDetails.filter(_.verified)))
      case None                       => internalServerErrorTemplate("Unable to obtain User information")
    }
  }

  def selectMfaPage(mfaAction: MfaAction): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(selectMfaView(SelectMfaForm.form, mfaAction)))
  }

  def selectMfaAction(mfaAction: MfaAction): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    SelectMfaForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(selectMfaView(form, mfaAction))),
      form => {
        mfaAction match {
          case CREATE => setupSelectedMfa(form.mfaType)
          case REMOVE => removeSelectedMfa(request.userId, form.mfaType)
        }
      }
    )
  }

  private def setupSelectedMfa(mfaType: String) = {
    MfaType.withNameInsensitive(mfaType) match {
      case SMS               => Future.successful(Redirect(routes.MfaController.setupSms()))
      case AUTHENTICATOR_APP => Future.successful(Redirect(routes.MfaController.authAppStart()))
    }
  }

  private def removeSelectedMfa(userId: UserId, mfaTypeStr: String)(implicit request: Request[_], hc: HeaderCarrier, messages: Messages) = {
    thirdPartyDeveloperConnector.fetchDeveloper(userId).map {
      case None                       => internalServerErrorTemplate("Unable to obtain user information")
      case Some(developer: Developer) =>
        val mfaType = MfaType.withNameInsensitive(mfaTypeStr)
        val mfaDetail = getMfaDetailByType(mfaType, developer.mfaDetails)
        mfaType match {
        case SMS               => Redirect(routes.MfaController.smsAccessCodePage(mfaDetail.id, MfaAction.REMOVE))
        case AUTHENTICATOR_APP => Redirect(routes.MfaController.authAppAccessCodePage(mfaDetail.id, MfaAction.REMOVE))
      }
    }
  }

  def authAppStart: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(authAppStartView()))
  }

  def setupAuthApp: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperMfaConnector.createMfaAuthApp(request.userId).map(registerAuthAppResponse => {
      val uri = otpAuthUri(registerAuthAppResponse.secret.toLowerCase, "HMRC Developer Hub", request.developerSession.email)
      val qrImg = qrCode.generateDataImageBase64(uri.toString)
      Ok(qrCodeView(registerAuthAppResponse.secret.toLowerCase().grouped(4).mkString(" "), qrImg, registerAuthAppResponse.mfaId))
    })
  }

  def authAppAccessCodePage(mfaId: MfaId, mfaAction: MfaAction): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(authAppAccessCodeView(MfaAccessCodeForm.form, mfaId, mfaAction)))
  }

  def authAppAccessCodeAction(mfaId: MfaId, mfaAction: MfaAction): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    def logonAndComplete(): Result = {
      thirdPartyDeveloperConnector.updateSessionLoggedInState(request.sessionId, UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN))
      Redirect(routes.MfaController.nameChangePage(mfaId))
    }

    def invalidCode(form: MfaAccessCodeForm): Result = {
      val mfaAccessCodeForm = MfaAccessCodeForm
        .form
        .fill(form)
        .withError(key = "accessCode", message = "You have entered an incorrect access code")

      BadRequest(authAppAccessCodeView(mfaAccessCodeForm, mfaId, mfaAction))
    }

    MfaAccessCodeForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(authAppAccessCodeView(form, mfaId, mfaAction))),
      form =>
        mfaAction match {
          case REMOVE => handleRemoveMfa(request.userId, mfaId, form.accessCode)
          case CREATE => for {
              mfaResponse <- mfaService.enableMfa(request.userId, mfaId, form.accessCode)
              result = if (mfaResponse.totpVerified) logonAndComplete() else invalidCode(form)
            } yield result
        }
    )
  }

  def nameChangePage(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(nameChangeView(MfaNameChangeForm.form, mfaId)))
  }

  def nameChangeAction(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    MfaNameChangeForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(nameChangeView(form, mfaId))),
      form => {
        thirdPartyDeveloperMfaConnector.changeName(request.userId, mfaId, form.name) map {
          case true  => Redirect(routes.MfaController.authAppSetupCompletedPage())
          case false => internalServerErrorTemplate("Failed to change MFA name")
        }
      }
    )
  }

  def authAppSetupCompletedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).map {
      case Some(developer: Developer) => Ok(authAppSetupCompletedView(!isSmsMfaVerified(developer.mfaDetails)))
      case None                       => internalServerErrorTemplate("Unable to obtain user information")
    }
  }

  def setupSms: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(mobileNumberView(MobileNumberForm.form)))
  }

  def setupSmsAction: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    MobileNumberForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(mobileNumberView(form))),
      form =>
        thirdPartyDeveloperMfaConnector.createMfaSms(request.userId, form.mobileNumber)
          .map(response =>
            Redirect(routes.MfaController.smsAccessCodePage(response.mfaId, MfaAction.CREATE))
              .flashing("mobileNumber" -> response.mobileNumber)
          )
    )
  }

  def smsAccessCodePage(mfaId: MfaId, mfaAction: MfaAction): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(smsAccessCodeView(SmsAccessCodeForm.form, mfaId, mfaAction)))
  }

  def smsAccessCodeAction(mfaId: MfaId, mfaAction: MfaAction): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    SmsAccessCodeForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(smsAccessCodeView(form, mfaId, mfaAction))),
      form =>
        mfaAction match {
          case CREATE => thirdPartyDeveloperMfaConnector.verifyMfa(request.userId, mfaId, form.accessCode) map {
              case true  => Redirect(routes.MfaController.smsSetupCompletedPage())
              case false => internalServerErrorTemplate("Unable to verify SMS access code")
            }
          case REMOVE => handleRemoveMfa(request.userId, mfaId, form.accessCode)
        }
    )
  }

  def smsSetupCompletedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).map {
      case Some(developer: Developer) => Ok(smsSetupCompletedView(!isAuthAppMfaVerified(developer.mfaDetails)))
      case None                       => internalServerErrorTemplate("Unable to obtain User information")
    }
  }

  def removeMfa(mfaId: MfaId, mfaType: MfaType): Action[AnyContent] = loggedInAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).flatMap {
      case Some(developer: Developer) =>
        val mfaDetails = developer.mfaDetails
        mfaDetails.size match {
          case 1 => removeMfaUserWithOneMfaMethod(mfaId, mfaType, request.userId)
          case 2 if hasVerifiedSmsAndAuthApp(mfaDetails) => Future.successful(Redirect(routes.MfaController.selectMfaPage(MfaAction.REMOVE)))
          case _ => Future.successful(internalServerErrorTemplate("MFA setup not valid"))
        }
      case None => Future.successful(internalServerErrorTemplate("Unable to obtain User information"))
    }
  }

  private def removeMfaUserWithOneMfaMethod(mfaId: MfaId, mfaType: MfaType, userId: UserId)(implicit hc: HeaderCarrier, request: Request[_]): Future[Result] = {
    mfaType match {
      case AUTHENTICATOR_APP => Future.successful(Redirect(routes.MfaController.authAppAccessCodePage(mfaId, MfaAction.REMOVE)))
      case SMS               =>
        thirdPartyDeveloperMfaConnector.sendSms(userId, mfaId).map {
          case true  => Redirect(routes.MfaController.smsAccessCodePage(mfaId, MfaAction.REMOVE))
          case false => internalServerErrorTemplate("Failed to send SMS")
        }
    }
  }

  private def handleRemoveMfa(userId: UserId, mfaId: MfaId,accessCode: String)(
    implicit request: Request[_], loggedIn: DeveloperSession, messages: Messages) = {

    mfaService.removeMfaById(userId, mfaId, accessCode) map {
      case MfaResponse(true)  => Ok(removeMfaCompletedView())
      case MfaResponse(false) => internalServerErrorTemplate("Unable to verify access code")
    }
  }

  private def internalServerErrorTemplate(errorMessage: String)(implicit request: Request[_]): Result = {
    InternalServerError(errorHandler.standardErrorTemplate(errorMessage, errorMessage, errorMessage))
  }
}
