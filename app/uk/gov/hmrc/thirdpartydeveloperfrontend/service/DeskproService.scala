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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{DeskproConnector, DeskproHorizonConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{SignOutSurveyForm, SupportEnquiryForm}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, Feedback, TicketCreated, TicketId, TicketResult}

@Singleton
class DeskproService @Inject() (
  val deskproConnector: DeskproConnector,
  val deskproHorizonConnector: DeskproHorizonConnector,
  val appConfig: ApplicationConfig
)(implicit val ec: ExecutionContext) {

  def submitSurvey(survey: SignOutSurveyForm)(implicit request: Request[AnyRef], hc: HeaderCarrier): Future[TicketId] = {
    val feedback = Feedback.createFromSurvey(survey, Option(appConfig.title))
    deskproConnector.createFeedback(feedback)
  }

  def submitSupportEnquiry(userId: Option[UserId], supportEnquiry: SupportEnquiryForm)(implicit request: Request[AnyRef], hc: HeaderCarrier): Future[TicketResult] = {
    val ticket = DeskproTicket.createFromSupportEnquiry(supportEnquiry, appConfig.title)

    for {
      deskproResult <- deskproConnector.createTicket(userId, ticket)
      // TODO: The Deskpro Horizon creation is included here for proof of concept. This needs to move to a separate service
      _             <- if (appConfig.useDesktopHorizon) deskproHorizonConnector.createTicket(ticket) else Future.successful(TicketCreated)
    } yield deskproResult
  }
}
