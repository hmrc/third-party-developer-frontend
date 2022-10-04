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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.models.{MfaId, MfaType}
import uk.gov.hmrc.apiplatform.modules.mfa.service.MFAService
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.{SecurityPreferencesView, SelectMfaView}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.{AuthAppAccessCodeView, AuthAppSetupCompletedView, AuthAppStartView, NameChangeView, QrCodeView}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.LoggedInController
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.UpdateLoggedInStateRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.qr.{OtpAuthUri, QRCode}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService
import play.api.data.Form
import play.api.data.Forms._
import uk.gov.hmrc.apiplatform.modules.mfa.forms.{MfaAccessCodeForm, MfaNameChangeForm, MobileNumberForm, SelectMfaForm, SmsAccessCodeForm}
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaType.{AUTHENTICATOR_APP, SMS}
import uk.gov.hmrc.apiplatform.modules.mfa.utils.MfaDetailHelper.{isAuthAppMfaVerified, isSmsMfaVerified}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.{MobileNumberView, SmsAccessCodeView, SmsSetupCompletedView}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MfaController @Inject()(
                               val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                               val thirdPartyDeveloperMfaConnector: ThirdPartyDeveloperMfaConnector,
                               val otpAuthUri: OtpAuthUri,
                               val mfaService: MFAService,
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
                               selectMfaView: SelectMfaView

)(implicit val ec: ExecutionContext,
  val appConfig: ApplicationConfig) extends LoggedInController(mcc) with WithUnsafeDefaultFormBinding {

  private val scale = 4
  val qrCode = QRCode(scale)

  def securityPreferences: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).map {
      case Some(developer: Developer) => Ok(securityPreferencesView(developer.mfaDetails.filter(_.verified)))
      case None => InternalServerError("unable to obtain User information")
    }
  }

  def selectMfaPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(selectMfaView(SelectMfaForm.form)))
  }

  def selectMfaAction: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>

    SelectMfaForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(selectMfaView(form))),
      form => {
        MfaType.withNameInsensitive(form.mfaType) match {
          case SMS => Future.successful(Redirect(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.MfaController.setupSms()))
          case AUTHENTICATOR_APP => Future.successful(Redirect(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.MfaController.authAppStart()))
        }
      }
    )
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

  def authAppAccessCodePage(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(authAppAccessCodeView(MfaAccessCodeForm.form, mfaId)))
  }

  def enableAuthApp(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>

    def logonAndComplete(): Result = {
      thirdPartyDeveloperConnector.updateSessionLoggedInState(request.sessionId, UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN))
      Redirect(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.MfaController.nameChangePage(mfaId))
    }

    def invalidCode(form: MfaAccessCodeForm): Result = {
      val mfaAccessCodeForm = MfaAccessCodeForm
        .form
        .fill(form)
        .withError(key = "accessCode", message = "You have entered an incorrect access code")

      BadRequest(authAppAccessCodeView(mfaAccessCodeForm, mfaId))
    }

    MfaAccessCodeForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(authAppAccessCodeView(form, mfaId))),
      form => {
        for {
          mfaResponse <- mfaService.enableMfa(request.userId, mfaId, form.accessCode)
          result = if (mfaResponse.totpVerified) logonAndComplete() else invalidCode(form)
        } yield result
      })
  }

  def nameChangePage(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(nameChangeView(MfaNameChangeForm.form, mfaId)))
  }

  def nameChangeAction(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>

    MfaNameChangeForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(nameChangeView(form, mfaId))),
      form => {
        thirdPartyDeveloperMfaConnector.changeName(request.userId, mfaId, form.name) map {
          case true => Redirect(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.MfaController.authAppSetupCompletedPage())
          case false => InternalServerError("Failed to change MFA name")
        }
      }
    )
  }

  def authAppSetupCompletedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).map {
      case Some(developer: Developer) => Ok(authAppSetupCompletedView(!isSmsMfaVerified(developer.mfaDetails)))
      case None => InternalServerError("Unable to obtain User information")
    }
  }

  def setupSms: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
      Future.successful(Ok(mobileNumberView(MobileNumberForm.form)))
  }

  def setupSmsAction: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    MobileNumberForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(mobileNumberView(form))),
      form => thirdPartyDeveloperMfaConnector.createMfaSms(request.userId, form.mobileNumber)
              .map(smsDetail => Redirect(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile
                .routes.MfaController.smsAccessCodePage(smsDetail.id)).flashing("mobileNumber" -> smsDetail.mobileNumber))
    )
  }

  def smsAccessCodePage(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).map {
      case Some(developer: Developer) => Ok(smsAccessCodeView(SmsAccessCodeForm.form, mfaId))
      case None => InternalServerError("Unable to obtain User information")
    }
  }

  def smsAccessCodeAction(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    SmsAccessCodeForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(smsAccessCodeView(form, mfaId))),
      form => thirdPartyDeveloperMfaConnector.verifyMfa(request.userId, mfaId, form.accessCode) map {
        case true => Redirect(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.MfaController.smsSetupCompletedPage())
        case false => InternalServerError("Unable to verify SMS access code")
      }
    )
  }

  def smsSetupCompletedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).map {
      case Some(developer: Developer) => Ok(smsSetupCompletedView(!isAuthAppMfaVerified(developer.mfaDetails)))
      case None => InternalServerError("Unable to obtain User information")
    }
  }
}


