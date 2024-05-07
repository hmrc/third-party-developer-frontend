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

import views.html.support.{CheckCdsAccessIsRequiredView, ChooseAPrivateApiView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, Call, MessagesControllerComponents, Result}
import play.twirl.api.HtmlFormat

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.MaybeUserRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService, SupportService}

@Singleton
class ChooseAPrivateApiController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val deskproService: DeskproService,
    supportService: SupportService,
    chooseAPrivateApiView: ChooseAPrivateApiView,
    ensureCdsAccessIsRequired: CheckCdsAccessIsRequiredView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractSupportFlowController[ChooseAPrivateApiForm, Unit](mcc, supportService) with SupportCookie {

  def redirectBack(): Result = Redirect(routes.HelpWithUsingAnApiController.page())

  def filterValidFlow(flow: SupportFlow): Boolean = flow match {
    case SupportFlow(_, SupportData.UsingAnApi.id, Some(SupportData.PrivateApiDocumentation.id), _, _, _, _) => true
    case _                                                                                                   => false
  }

  def pageContents(flow: SupportFlow, form: Form[ChooseAPrivateApiForm], extras: Unit)(implicit request: MaybeUserRequest[AnyContent]): HtmlFormat.Appendable =
    chooseAPrivateApiView(
      fullyloggedInDeveloper,
      form,
      routes.HelpWithUsingAnApiController.page().url
    )

  def choose(choice: String)(flow: SupportFlow) =
    flow.copy(privateApi = Some(choice))

  def updateFlowAndRedirect(flowFn: SupportFlow => SupportFlow)(redirectTo: Call)(flow: SupportFlow) = {
    supportService.updateWithDelta(flowFn)(flow).map { newFlow =>
      Redirect(redirectTo)
    }
  }

  def chooseBusinessRates(flow: SupportFlow) = choose(SupportData.ChooseBusinessRates.text)(flow)
  def chooseCDS(flow: SupportFlow)           = choose(SupportData.ChooseCDS.text)(flow)

  def onValidForm(flow: SupportFlow, form: ChooseAPrivateApiForm)(implicit request: MaybeUserRequest[AnyContent]): Future[Result] =
    form.chosenApiName match {
      case c @ SupportData.ChooseBusinessRates.id => updateFlowAndRedirect(chooseBusinessRates)(routes.ApplyForPrivateApiAccessController.page())(flow)
      case c @ SupportData.ChooseCDS.id           => updateFlowAndRedirect(chooseCDS)(routes.CheckCdsAccessIsRequiredController.page())(flow)
    }

  def form(): Form[ChooseAPrivateApiForm] = ChooseAPrivateApiForm.form

  def extraData()(implicit request: MaybeUserRequest[AnyContent]): Future[Unit] = successful(())
}
