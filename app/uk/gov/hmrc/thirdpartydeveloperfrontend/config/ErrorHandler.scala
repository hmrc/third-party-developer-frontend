/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.config

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import views.html.{ErrorTemplate, ForbiddenTemplate}

import play.api.Configuration
import play.api.i18n.MessagesApi
import play.api.mvc.Results.NotFound
import play.api.mvc.{Request, RequestHeader, Result}
import play.twirl.api.Html
import uk.gov.hmrc.play.bootstrap.frontend.http.FrontendErrorHandler

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.{ApiContextVersionNotFound, ApplicationNotFound}

@Singleton
class ErrorHandler @Inject() (
    val messagesApi: MessagesApi,
    val configuration: Configuration,
    errorTemplateView: ErrorTemplate,
    forbiddenTemplateView: ForbiddenTemplate
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends FrontendErrorHandler {

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit request: RequestHeader): Future[Html] = {
    successful(errorTemplateView(pageTitle, heading, message))
  }

  def forbiddenTemplate(implicit request: Request[_]): Future[Html] = {
    successful(forbiddenTemplateView())
  }

  override def resolveError(rh: RequestHeader, ex: Throwable): Future[Result] = {
    implicit val r: Request[String] = Request(rh, "")

    ex match {
      case _: ApplicationNotFound       => notFoundTemplate.map(NotFound(_))
      case _: ApiContextVersionNotFound => notFoundTemplate.map(NotFound(_))
      case _                            => super.resolveError(rh, ex)
    }
  }
}
