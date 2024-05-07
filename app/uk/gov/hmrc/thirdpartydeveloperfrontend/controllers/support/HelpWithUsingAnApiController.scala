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

import views.html.support.HelpWithUsingAnApiView

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, Call, MessagesControllerComponents, Result}
import play.twirl.api.HtmlFormat

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.MaybeUserRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService, SupportService}

object HelpWithUsingAnApiController {

  def chooseMakingCall(form: HelpWithUsingAnApiForm)(flow: SupportFlow) =
    flow.copy(
      subSelection = Some(SupportData.MakingAnApiCall.id),
      api = Some(form.apiNameForCall)
    )

  def chooseGettingExamples(form: HelpWithUsingAnApiForm)(flow: SupportFlow) =
    flow.copy(
      subSelection = Some(SupportData.GettingExamples.id),
      api = Some(form.apiNameForExamples)
    )

  def chooseReporting(form: HelpWithUsingAnApiForm)(flow: SupportFlow) =
    flow.copy(
      subSelection = Some(SupportData.ReportingDocumentation.id),
      api = Some(form.apiNameForReporting)
    )

  def choosePrivateApi(form: HelpWithUsingAnApiForm)(flow: SupportFlow) =
    flow.copy(subSelection = Some(SupportData.PrivateApiDocumentation.id))
}

@Singleton
class HelpWithUsingAnApiController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val deskproService: DeskproService,
    supportService: SupportService,
    helpWithUsingAnApiView: HelpWithUsingAnApiView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractSupportFlowController[HelpWithUsingAnApiForm, List[ApiDefinition]](mcc, supportService) with SupportCookie {

  import HelpWithUsingAnApiController._

  def redirectBack(): Result = Redirect(routes.SupportEnquiryInitialChoiceController.page())

  def filterValidFlow(flow: SupportFlow): Boolean = flow match {
    case SupportFlow(_, SupportData.UsingAnApi.id, _, _, _, _, _) => true
    case _                                                        => false
  }

  def pageContents(flow: SupportFlow, form: Form[HelpWithUsingAnApiForm], extras: List[ApiDefinition])(implicit request: MaybeUserRequest[AnyContent]): HtmlFormat.Appendable =
    helpWithUsingAnApiView(
      fullyloggedInDeveloper,
      form,
      routes.SupportEnquiryController.supportEnquiryPage(true).url,
      extras
    )

  def updateFlowAndRedirect(flowFn: SupportFlow => SupportFlow)(redirectTo: Call)(flow: SupportFlow) = {
    supportService.updateWithDelta(flowFn)(flow).map { newFlow =>
      Redirect(redirectTo)
    }
  }

  def onValidForm(flow: SupportFlow, form: HelpWithUsingAnApiForm)(implicit request: MaybeUserRequest[AnyContent]): Future[Result] = {
    form.choice match {
      case SupportData.MakingAnApiCall.id         => updateFlowAndRedirect(chooseMakingCall(form))(routes.SupportDetailsController.supportDetailsPage())(flow)
      case SupportData.GettingExamples.id         => updateFlowAndRedirect(chooseGettingExamples(form))(routes.SupportDetailsController.supportDetailsPage())(flow)
      case SupportData.ReportingDocumentation.id  => updateFlowAndRedirect(chooseReporting(form))(routes.SupportDetailsController.supportDetailsPage())(flow)
      case SupportData.PrivateApiDocumentation.id => updateFlowAndRedirect(choosePrivateApi(form))(routes.ChooseAPrivateApiController.page())(flow)
    }
  }

  def form(): Form[HelpWithUsingAnApiForm] = HelpWithUsingAnApiForm.form

  def extraData()(implicit request: MaybeUserRequest[AnyContent]): Future[List[ApiDefinition]] = supportService.fetchAllPublicApis(request.developerSession.map(_.developer.userId))

}
