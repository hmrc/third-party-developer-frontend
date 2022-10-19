/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.dynamics.controllers

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.apiplatform.modules.dynamics.connectors.{ThirdPartyDeveloperDynamicsConnector, Ticket}
import uk.gov.hmrc.apiplatform.modules.dynamics.model.AddTicketForm
import uk.gov.hmrc.apiplatform.modules.dynamics.views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.LoggedInController
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DynamicsController @Inject() (
    val thirdPartyDeveloperDynamicsConnector: ThirdPartyDeveloperDynamicsConnector,
    val ticketsView: TicketsView,
    val addTicketView: AddTicketView,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val errorHandler: ErrorHandler,
    val cookieSigner: CookieSigner
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig)
    extends LoggedInController(mcc) {

  def tickets: Action[AnyContent] = loggedInAction { implicit request =>
    thirdPartyDeveloperDynamicsConnector.getTickets().map { tickets: List[Ticket] =>
      Ok(ticketsView(tickets))
    } recover {
      case _ => InternalServerError(errorHandler.standardErrorTemplate("", "MS Dynamics Tickets", "Cannot get tickets"))
    }
  }

  def addTicket(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(addTicketView(AddTicketForm.form)))
  }

  def addTicketAction(): Action[AnyContent] = loggedInAction { implicit request =>
    AddTicketForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(addTicketView(form))),
      form => {
        thirdPartyDeveloperDynamicsConnector.createTicket(form.customerId, form.title, form.description).map {
          case Right(_)           => Redirect(uk.gov.hmrc.apiplatform.modules.dynamics.controllers.routes.DynamicsController.tickets())
          case Left(errorMessage) =>
            val createIncidentForm = AddTicketForm
              .form
              .fill(form)
              .withError(key = "dynamics", message = errorMessage)

            BadRequest(addTicketView(createIncidentForm))
        }
      }
    )
  }
}
