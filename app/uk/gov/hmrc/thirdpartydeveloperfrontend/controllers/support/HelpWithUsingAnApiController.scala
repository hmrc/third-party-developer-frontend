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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import views.html.support.HelpWithUsingAnApiView

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
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
    def renderPage(flow: SupportFlow): Future[Result] =
      if (flow.entrySelection == SupportData.UsingAnApi.id)
        supportService.fetchAllPublicApis(request.developerSession.map(_.developer.userId)).map { apis =>
          Ok(
            helpWithUsingAnApiView(
              fullyloggedInDeveloper,
              HelpWithUsingAnApiForm.form,
              routes.SupportEnquiryController.supportEnquiryPage(true).url,
              apis
            )
          )
        }
      else
        successful(Redirect(routes.SupportEnquiryController.supportEnquiryPage(true)))

    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
    supportService.getSupportFlow(sessionId).flatMap(renderPage)
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

    def redirectToChoosePrivateApiPage(sessionId: String, onFlow: => Future[Either[Throwable, SupportFlow]]): Future[Result] =
      onFlow.flatMap {
        case Right(_) => Future.successful(withSupportCookie(Redirect(routes.ChooseAPrivateApiController.chooseAPrivateApiPage()), sessionId))
        case Left(_)  => renderHelpWithUsingAnApiErrorView(HelpWithUsingAnApiForm.form.withError("error", "Error"))
      }

    def redirectToSupportDetailsPageOnFlow(sessionId: String, onFlow: => Future[Either[Throwable, SupportFlow]]): Future[Result] =
      onFlow.flatMap {
        case Right(_) => Future.successful(withSupportCookie(Redirect(routes.SupportDetailsController.supportDetailsPage()), sessionId))
        case Left(_)  => renderHelpWithUsingAnApiErrorView(HelpWithUsingAnApiForm.form.withError("error", "Error"))
      }

    def clearAnyApiChoiceAndRedirectToChoosePrivateApiPage(): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      redirectToChoosePrivateApiPage(sessionId, supportService.updateApiSubselection(sessionId, SupportData.PrivateApiDocumentation.id))
    }

    def clearAnyApiChoiceAndRedirectToSupportDetailsPage(): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      redirectToSupportDetailsPageOnFlow(sessionId, supportService.clearApiChoice(sessionId))
    }

    def updateFlowAndRedirect(usingApiSubSelection: String, apiName: String): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      redirectToSupportDetailsPageOnFlow(sessionId, supportService.updateApiChoice(sessionId, usingApiSubSelection, ServiceName(apiName)))
    }

    def handleValidForm(form: HelpWithUsingAnApiForm): Future[Result] = {
      form.choice match {
        case SupportData.MakingAnApiCall.id         => updateFlowAndRedirect(SupportData.MakingAnApiCall.id, form.apiNameForCall)
        case SupportData.GettingExamples.id         => updateFlowAndRedirect(SupportData.GettingExamples.id, form.apiNameForExamples)
        case SupportData.ReportingDocumentation.id  => updateFlowAndRedirect(SupportData.ReportingDocumentation.id, form.apiNameForReporting)
        case SupportData.PrivateApiDocumentation.id => clearAnyApiChoiceAndRedirectToChoosePrivateApiPage()
        case _                                      => clearAnyApiChoiceAndRedirectToSupportDetailsPage()
      }
    }

    def handleInvalidForm(formWithErrors: Form[HelpWithUsingAnApiForm]): Future[Result] = {
      renderHelpWithUsingAnApiErrorView(formWithErrors)
    }

    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
    supportService.getSupportFlow(sessionId).flatMap { flow =>
      if (flow.entrySelection == SupportData.UsingAnApi.id) {
        HelpWithUsingAnApiForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
      } else {
        successful(Redirect(routes.SupportEnquiryController.supportEnquiryPage(true)))
      }
    }

  }
}
