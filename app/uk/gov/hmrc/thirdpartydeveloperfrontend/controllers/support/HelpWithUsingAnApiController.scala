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

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.html.support.HelpWithUsingAnApiView

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SupportData
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService, SupportService}

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
) extends AbstractController(mcc) with SupportCookie {

  def helpWithUsingAnApiPage: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    for {
      apis <- supportService.fetchAllPublicApis(request.developerSession.map(_.developer.userId))
    } yield Ok(
      helpWithUsingAnApiView(
        fullyloggedInDeveloper,
        HelpWithUsingAnApiForm.form,
        routes.SupportEnquiryController.supportEnquiryPage(true).url,
        apis
      )
    )
  }

  def submitHelpWithUsingAnApi: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderHelpWithUsingAnApiErrorView(form: Form[HelpWithUsingAnApiForm]) = {
      for {
        apis <- supportService.fetchAllPublicApis(request.developerSession.map(_.developer.userId))
      } yield BadRequest(
        helpWithUsingAnApiView(
          fullyloggedInDeveloper,
          form,
          routes.SupportEnquiryController.supportEnquiryPage(true).url,
          apis
        )
      )
    }

    // def redirectToPrivateApiDocPageOnFlow(sessionId: String, onFlow: => Future[Either[Throwable, SupportFlow]]): Future[Result] =
    //   onFlow.flatMap {
    //     case Right(_) => Future.successful(withSupportCookie(Redirect(routes.SupportData.supportPrivateApiDocumentationPage()), sessionId))
    //     case Left(_)  => renderApiSupportPageErrorView(HelpWithApiForm.form.withError("error", "Error"))
    //   }

    def redirectToDetailsPageOnFlow(sessionId: String, onFlow: => Future[Either[Throwable, SupportFlow]]): Future[Result] =
      onFlow.flatMap {
        case Right(_) => Future.successful(withSupportCookie(Redirect(routes.SupportDetailsController.supportDetailsPage()), sessionId))
        case Left(_)  => renderHelpWithUsingAnApiErrorView(HelpWithUsingAnApiForm.form.withError("error", "Error"))
      }

    def clearAnyApiChoiceAndRedirect(): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      redirectToDetailsPageOnFlow(sessionId, supportService.clearApiChoice(sessionId))
    }

    def updateFlowAndRedirect(usingApiSubSelection: String, apiName: String): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      redirectToDetailsPageOnFlow(sessionId, supportService.updateApiChoice(sessionId, ServiceName(apiName), usingApiSubSelection))
    }

    def handleValidForm(form: HelpWithUsingAnApiForm): Future[Result] = {
      form.choice match {
        case SupportData.MakingAnApiCall.text         => updateFlowAndRedirect(SupportData.MakingAnApiCall.id, form.apiNameForCall)
        case SupportData.GettingExamples.text         => updateFlowAndRedirect(SupportData.GettingExamples.id, form.apiNameForExamples)
        case SupportData.ReportingDocumentation.text  => updateFlowAndRedirect(SupportData.ReportingDocumentation.id, form.apiNameForReporting)
        case SupportData.PrivateApiDocumentation.text => updateFlowAndRedirect(SupportData.PrivateApiDocumentation.id, form.apiNameForReporting) // TODO <- FIXME
        case _                                        => clearAnyApiChoiceAndRedirect()
      }
    }

    def handleInvalidForm(formWithErrors: Form[HelpWithUsingAnApiForm]): Future[Result] =
      renderHelpWithUsingAnApiErrorView(formWithErrors)

      println(request.body)
      println(HelpWithUsingAnApiForm.form.bindFromRequest())
    HelpWithUsingAnApiForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }
}
