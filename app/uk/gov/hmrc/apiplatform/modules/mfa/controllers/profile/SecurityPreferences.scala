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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.service.{MFAService, MfaMandateService}
import uk.gov.hmrc.apiplatform.modules.mfa.utils.MfaDetailHelper.{isAuthAppMfaVerified, isSmsMfaVerified}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.SecurityPreferencesView
import uk.gov.hmrc.play.bootstrap.controller.WithDefaultFormBinding
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.LoggedInController
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.Developer
import uk.gov.hmrc.thirdpartydeveloperfrontend.qr.OtpAuthUri
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SecurityPreferences @Inject()(
  val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
  val thirdPartyDeveloperMfaConnector: ThirdPartyDeveloperMfaConnector,
  val otpAuthUri: OtpAuthUri,
  val mfaService: MFAService,
  val sessionService: SessionService,
  mcc: MessagesControllerComponents,
  val errorHandler: ErrorHandler,
  val mfaMandateService: MfaMandateService,
  val cookieSigner: CookieSigner,
  val securityPreferencesView: SecurityPreferencesView
)(implicit val ec: ExecutionContext,
  val appConfig: ApplicationConfig) extends LoggedInController(mcc) with WithDefaultFormBinding {


  def securityPreferences: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(request.userId).map {
      case Some(developer: Developer) => Ok(securityPreferencesView(developer.mfaDetails))
      case None => throw new RuntimeException
    }
  }



}


