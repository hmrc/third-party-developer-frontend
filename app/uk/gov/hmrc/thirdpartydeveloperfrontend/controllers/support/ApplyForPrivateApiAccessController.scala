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
import scala.concurrent.{ExecutionContext, Future}

import views.html.support.{ApplyForPrivateApiAccessView, ChooseAPrivateApiView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, MessagesControllerComponents, Result}
import play.twirl.api.HtmlFormat

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.MaybeUserRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService, SupportService}
import scala.concurrent.Future.successful

@Singleton
class ApplyForPrivateApiAccessController @Inject() (
    mcc: MessagesControllerComponents,
    supportService: SupportService,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val deskproService: DeskproService,
    applyForPrivateApiAccessView: ApplyForPrivateApiAccessView,
    chooseAPrivateApiView: ChooseAPrivateApiView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractSupportFlowController[ApplyForPrivateApiAccessForm, Unit](mcc, supportService) with SupportCookie {

  def redirectBack(): Result = Redirect(routes.ChooseAPrivateApiController.page())

  def filterValidFlow(flow: SupportFlow): Boolean = flow.privateApi.isDefined

  def form() = ApplyForPrivateApiAccessForm.form

  def extraData()(implicit request: MaybeUserRequest[AnyContent]): Future[Unit] = successful(())

  def pageContents(flow: SupportFlow, form: Form[ApplyForPrivateApiAccessForm], extras: Unit)(implicit request: MaybeUserRequest[AnyContent]): HtmlFormat.Appendable = {
    applyForPrivateApiAccessView(
      fullyloggedInDeveloper,
      flow.privateApi.get,
      form,
      routes.ChooseAPrivateApiController.page().url
    )
  }

  def onValidForm(flow: SupportFlow, form: ApplyForPrivateApiAccessForm)(implicit request: MaybeUserRequest[AnyContent]): Future[Result] = {
    supportService.submitTicket(flow, form).map { _ =>
      Redirect(routes.SupportDetailsController.supportConfirmationPage())
    }
  }
}
