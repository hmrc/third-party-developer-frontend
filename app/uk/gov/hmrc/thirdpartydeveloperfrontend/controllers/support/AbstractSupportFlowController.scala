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
import scala.concurrent.Future
import scala.concurrent.Future.successful

import play.api.data.Form
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.twirl.api.HtmlFormat

import uk.gov.hmrc.apiplatform.modules.tpd.domain.models.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SupportService

abstract class AbstractSupportFlowController[F, E](
    mcc: MessagesControllerComponents,
    supportService: SupportService
  ) extends BaseController(mcc) with SupportCookie {

  val supportForm: Form[SupportEnquiryForm] = SupportEnquiryForm.form

  protected def fullyloggedInDeveloper(implicit request: MaybeUserRequest[AnyContent]): Option[DeveloperSession] =
    request.developerSession.filter(_.loggedInState.isLoggedIn)

  def redirectBack(): Result

  def filterValidFlow(flow: SupportFlow): Boolean

  def pageContents(flow: SupportFlow, form: Form[F], extras: E)(implicit request: MaybeUserRequest[AnyContent]): HtmlFormat.Appendable

  def onValidForm(flow: SupportFlow, form: F)(implicit request: MaybeUserRequest[AnyContent]): Future[Result]

  def form(): Form[F]

  // Typically can be successful(Unit) if nothing is needed (see HelpWithUsingAnApiController for use to get api list)
  def extraData()(implicit request: MaybeUserRequest[AnyContent]): Future[E]

  private final def performActionIfFlowValid(flow: SupportFlow)(action: SupportFlow => Result): Future[Result] = {
    Some(flow).filter(filterValidFlow).fold(successful(redirectBack()))(f => successful(action(f)))
  }

  private final def performAsyncActionIfFlowValid(flow: SupportFlow)(action: SupportFlow => Future[Result]): Future[Result] = {
    Some(flow).filter(filterValidFlow).fold(successful(redirectBack()))(action)
  }

  private final def lookupFlow(implicit request: MaybeUserRequest[AnyContent]): Future[SupportFlow] = {
    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
    supportService.getSupportFlow(sessionId)
  }

  private final def renderPageWhenFlowIsValid(render: (SupportFlow, E) => Result)(implicit request: MaybeUserRequest[AnyContent]): Future[Result] = {
    lookupFlow.flatMap { flow =>
      extraData().flatMap { extras =>
        performActionIfFlowValid(flow) { validFlow =>
          render(validFlow, extras)
        }
      }
    }
  }

  final def page(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    renderPageWhenFlowIsValid { (flow, extras) =>
      Ok(pageContents(flow, form(), extras))
    }
  }

  def otherValidation(flow: SupportFlow, extraData: E, form: Form[F]): Form[F] = {
    form
  }

  final def submit(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    lookupFlow.flatMap { flow =>
      extraData().flatMap { extras =>
        performAsyncActionIfFlowValid(flow) { validFlow =>
          def handleInvalidForm(formWithErrors: Form[F]) = successful(BadRequest(pageContents(validFlow, formWithErrors, extras)))
          otherValidation(flow, extras, form().bindFromRequest()).fold(handleInvalidForm, form => onValidForm(validFlow, form)(request))
        }
      }
    }
  }

}
