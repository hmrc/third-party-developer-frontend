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
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.apiplatform.modules.mfa.service.MFAService
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.SecurityPreferencesView
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.{AccessCodeView, AuthAppSetupCompletedView, AuthAppStartView, NameChangeView, QrCodeView}
import uk.gov.hmrc.play.bootstrap.controller.WithDefaultFormBinding
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{FormKeys, LoggedInController}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.UpdateLoggedInStateRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.qr.{OtpAuthUri, QRCode}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService
import play.api.data.Form
import play.api.data.Forms._
import uk.gov.hmrc.apiplatform.modules.mfa.forms.{MfaAccessCodeForm, MfaNameChangeForm}

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
                               accessCodeView: AccessCodeView,
                               qrCodeView: QrCodeView,
                               authAppSetupCompletedView: AuthAppSetupCompletedView,
                               nameChangeView: NameChangeView

)(implicit val ec: ExecutionContext,
  val appConfig: ApplicationConfig) extends LoggedInController(mcc) with WithDefaultFormBinding {


  def securityPreferences: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).map {
      case Some(developer: Developer) => Ok(securityPreferencesView(developer.mfaDetails.filter(_.verified)))
      case None => InternalServerError("unable to obtain User information")
    }
  }

  def authAppStart: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(authAppStartView()))
  }

  private val scale = 4
  val qrCode = QRCode(scale)

  def setupAuthApp: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperMfaConnector.createMfaSecret(request.userId).map(registerAuthAppResponse => {
      val uri = otpAuthUri(registerAuthAppResponse.secret.toLowerCase, "HMRC Developer Hub", request.developerSession.email)
      val qrImg = qrCode.generateDataImageBase64(uri.toString)
      Ok(qrCodeView(registerAuthAppResponse.secret.toLowerCase().grouped(4).mkString(" "), qrImg, registerAuthAppResponse.mfaId))
    })
  }


  def getAccessCodePage(mfaId: MfaId): Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(accessCodeView(MfaAccessCodeForm.form, mfaId)))
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

      BadRequest(accessCodeView(mfaAccessCodeForm, mfaId))
    }

    MfaAccessCodeForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(accessCodeView(form, mfaId))),
      form => {
        for {
          mfaResponse <- mfaService.enableMfa(request.userId, mfaId, form.accessCode)
          result = {
            if (mfaResponse.totpVerified) logonAndComplete()
            else invalidCode(form)
          }
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
    Future.successful(Ok(authAppSetupCompletedView()))
  }

}


