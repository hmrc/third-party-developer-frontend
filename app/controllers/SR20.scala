/*
 * Copyright 2021 HM Revenue & Customs
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

/*
 * Copyright 2021 HM Revenue & Customs
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
import connectors.ApmConnector
import controllers.checkpages.CanUseCheckActions
import domain.models
import domain.models.applications.ApplicationId
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import service.{ApplicationActionService, ApplicationService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.ConfirmApisView

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SR20 @Inject() (val errorHandler: ErrorHandler,
                      val sessionService: SessionService,
                      val applicationActionService: ApplicationActionService,
                      val applicationService: ApplicationService,
                      mcc: MessagesControllerComponents,
                      val cookieSigner: CookieSigner,
                      confirmApisView: ConfirmApisView,
                      val apmConnector: ApmConnector)
                     (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions{
       
  def confirmApiSubscription(sandboxAppId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>
    for {
      allApis <- apmConnector.fetchAllApis(models.applications.Environment.SANDBOX)
      upliftableSubscriptions <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
    }
    yield {
      val upliftableSubscriptionsWithData = upliftableSubscriptions
        .flatMap(upliftableSubscription => allApis
        .get(upliftableSubscription.context)
        .map { apiData => (apiData.name, upliftableSubscription.version.value) })

      Ok(confirmApisView(sandboxAppId, upliftableSubscriptionsWithData))
    }
  }

  protected def confirmApiSubscriptionRoute(appId: ApplicationId): Call = routes.SR20.confirmApiSubscription(appId)

  def upliftSandboxApplication(sandboxAppId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>
    upliftApplicationAndShowRequestCheckPage(sandboxAppId)
  }

  private def upliftApplicationAndShowRequestCheckPage(sandboxAppId: ApplicationId)(implicit hc: HeaderCarrier) = {
    for {
      newAppId <- apmConnector.upliftApplication(sandboxAppId)
    } yield Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(newAppId))
  }
}
