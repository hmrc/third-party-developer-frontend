/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import views.html.support.HelpWithApplicationsView

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, MessagesControllerComponents, Result}
import play.twirl.api.HtmlFormat

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.MaybeUserRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

object HelpWithApplicationsController {

  def choose(form: HelpWithApplicationsForm)(flow: SupportFlow) =
    flow.copy(
      subSelection = Some(form.choice)
    )
}

@Singleton
class HelpWithApplicationsController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val deskproService: DeskproService,
    supportService: SupportService,
    helpWithApplicationsView: HelpWithApplicationsView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractSupportFlowController[HelpWithApplicationsForm, Unit](mcc, supportService) with SupportCookie {

  import HelpWithApplicationsController._

  def redirectBack(): Result = Redirect(routes.SupportEnquiryInitialChoiceController.page())

  def filterValidFlow(flow: SupportFlow): Boolean = flow match {
    case SupportFlow(_, SupportData.SettingUpApplication.id, _, _, _, _, _) => true
    case _                                                                  => false
  }

  def pageContents(flow: SupportFlow, form: Form[HelpWithApplicationsForm], extras: Unit)(implicit request: MaybeUserRequest[AnyContent]): HtmlFormat.Appendable =
    helpWithApplicationsView(
      fullyloggedInDeveloper,
      form,
      routes.HelpWithApplicationsController.page().url
    )

  def onValidForm(flow: SupportFlow, form: HelpWithApplicationsForm)(implicit request: MaybeUserRequest[AnyContent]): Future[Result] = {
    form.choice match {
      case _ =>
        supportService.updateWithDelta(choose(form))(flow).map { newFlow =>
          Redirect(routes.SupportDetailsController.supportDetailsPage())
        }
    }
  }

  def form(): Form[HelpWithApplicationsForm] = HelpWithApplicationsForm.form

  // Typically can be successful(Unit) if nothing is needed (see HelpWithUsingAnApiController for use to get api list)
  def extraData()(implicit request: MaybeUserRequest[AnyContent]): Future[Unit] = successful(())
}
