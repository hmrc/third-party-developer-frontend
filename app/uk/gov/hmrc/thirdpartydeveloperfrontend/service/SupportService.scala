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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.common.services.{ApplicationLogger, EitherTHelper}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, DeskproHorizonConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApiSupportDetailsForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproHorizonTicket, DeskproHorizonTicketMessage, DeskproHorizonTicketPerson}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{SupportApi, SupportFlow}
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository

@Singleton
class SupportService @Inject() (
    val apmConnector: ApmConnector,
    deskproConnector: DeskproHorizonConnector,
    flowRepository: FlowRepository,
    config: ApplicationConfig
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger {

  val ET = EitherTHelper.make[Throwable]

  private def fetchSupportFlow(sessionId: String): Future[SupportFlow] = {
    flowRepository.fetchBySessionIdAndFlowType[SupportFlow](sessionId) map {
      case Some(flow) => flow
      case None       => SupportFlow(sessionId, "unknown")
    }
  }

  def getSupportFlow(sessionId: String): Future[SupportFlow] = {
    for {
      flow      <- fetchSupportFlow(sessionId)
      savedFlow <- flowRepository.saveFlow(flow)
    } yield savedFlow
  }

  def updateApiChoice(sessionId: String, apiChoice: ServiceName)(implicit hc: HeaderCarrier): Future[Either[Throwable, SupportFlow]] = {
    (
      for {
        flow       <- ET.liftF(fetchSupportFlow(sessionId))
        apiName    <- if (apiChoice.value == "api-not-in-list") ET.liftF(Future.successful(""))
                      else ET.fromEitherF(apmConnector.fetchExtendedApiDefinition(apiChoice)).map(_.name)
        updatedFlow = flow.copy(api = Some(SupportApi(apiChoice, apiName)))
        savedFlow  <- ET.liftF(flowRepository.saveFlow(updatedFlow))
      } yield savedFlow
    ).value
  }

  def createFlow(sessionId: String, entrypoint: String): Future[SupportFlow] = {
    flowRepository.saveFlow(SupportFlow(sessionId, entrypoint))
  }

  def fetchAllPublicApis(userId: Option[UserId])(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] = {
    apmConnector.fetchApiDefinitionsVisibleToUser(userId)
  }

  def submitTicket(supportFlow: SupportFlow, form: ApiSupportDetailsForm)(implicit hc: HeaderCarrier): Future[SupportFlow] = {
    deskproConnector.createTicket(DeskproHorizonTicket(
      person = DeskproHorizonTicketPerson(form.fullName, form.emailAddress),
      subject = "HMRC Developer Hub: Support Enquiry",
      message = DeskproHorizonTicketMessage.fromRaw(form.details),
      brand = config.deskproHorizonBrand,
      fields = Map(config.deskproHorizonApiName -> supportFlow.api.map(_.name).getOrElse(""), config.deskproHorizonEntryPoint -> supportFlow.entrySelection)
    )).flatMap { result =>
      flowRepository.saveFlow(supportFlow.copy(referenceNumber = Some(result.ref), emailAddress = Some(form.emailAddress)))
    }
  }
}
