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
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support.{SupportData, SupportDetailsForm}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproHorizonTicket, DeskproHorizonTicketMessage, DeskproHorizonTicketPerson}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{SupportApi, SupportFlow}
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support.ApplyForPrivateApiAccessForm

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
      case None       => SupportFlow(sessionId, "unknown", None)
    }
  }

  def getSupportFlow(sessionId: String): Future[SupportFlow] = {
    for {
      flow      <- fetchSupportFlow(sessionId)
      savedFlow <- flowRepository.saveFlow(flow)
    } yield savedFlow
  }

  def clearApiChoice(sessionId: String): Future[Either[Throwable, SupportFlow]] = {
    (
      for {
        flow       <- ET.liftF(fetchSupportFlow(sessionId))
        updatedFlow = flow.copy(api = None, subSelection = None)
        savedFlow  <- ET.liftF(flowRepository.saveFlow(updatedFlow))
      } yield savedFlow
    ).value
  }

  def updateApiChoice(sessionId: String, usingApiSubSelection: String, apiChoice: ServiceName)(implicit hc: HeaderCarrier): Future[Either[Throwable, SupportFlow]] = {
    (
      for {
        flow       <- ET.liftF(fetchSupportFlow(sessionId))
        apiName    <- if (apiChoice.value == "api-not-in-list") ET.liftF(Future.successful(""))
                      else ET.fromEitherF(apmConnector.fetchExtendedApiDefinition(apiChoice)).map(_.name)
        updatedFlow = flow.copy(api = Some(SupportApi(apiChoice, apiName)), subSelection = Some(usingApiSubSelection))
        savedFlow  <- ET.liftF(flowRepository.saveFlow(updatedFlow))
      } yield savedFlow
    ).value
  }

  def setPrivateApiChoice(sessionId: String, apiChoice: String)(implicit hc: HeaderCarrier): Future[Either[Throwable, SupportFlow]] = {
    (
      for {
        flow       <- ET.liftF(fetchSupportFlow(sessionId))
        updatedFlow = flow.copy(subSelection = Some(SupportData.PrivateApiDocumentation.id), privateApi = Some(apiChoice))
        savedFlow  <- ET.liftF(flowRepository.saveFlow(updatedFlow))
      } yield savedFlow
    ).value
  }

  def createFlow(sessionId: String, entrypoint: String): Future[SupportFlow] = {
    flowRepository.saveFlow(SupportFlow(sessionId, entrypoint, None))
  }

  def fetchAllPublicApis(userId: Option[UserId])(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] = {
    apmConnector.fetchApiDefinitionsVisibleToUser(userId)
  }

  def submitTicket(supportFlow: SupportFlow, form: SupportDetailsForm)(implicit hc: HeaderCarrier): Future[SupportFlow] = {
    submitTicket(supportFlow, form.fullName, form.emailAddress, form.details)
  }
  
  def submitTicket(supportFlow: SupportFlow, form: ApplyForPrivateApiAccessForm)(implicit hc: HeaderCarrier): Future[SupportFlow] = {
    submitTicket(supportFlow, form.fullName, form.emailAddress, "???")
  }

  private def submitTicket(supportFlow: SupportFlow, fullName: String, emailAddress: String, messageContents: String)(implicit hc: HeaderCarrier): Future[SupportFlow] = {
    // Entry point is currently the value of the text on the radio button but may not always be so.
    def deriveEntryPoint(): String = {
      (supportFlow.entrySelection, supportFlow.subSelection) match {
        case (SupportData.FindingAnApi.id, _)                                           => SupportData.FindingAnApi.text
        case (SupportData.UsingAnApi.id, Some(SupportData.MakingAnApiCall.id))          => SupportData.MakingAnApiCall.text
        case (SupportData.UsingAnApi.id, Some(SupportData.GettingExamples.id))          => SupportData.GettingExamples.text
        case (SupportData.UsingAnApi.id, Some(SupportData.ReportingDocumentation.id))   => SupportData.ReportingDocumentation.text
        case (SupportData.UsingAnApi.id, Some(SupportData.PrivateApiDocumentation.id))  => SupportData.PrivateApiDocumentation.text
        case (SupportData.SigningIn.id, _)                                              => SupportData.SigningIn.text
        case (SupportData.SettingUpApplication.id, _)                                   => SupportData.SettingUpApplication.text
        case (SupportData.ReportingDocumentation.id, _)                                 => SupportData.ReportingDocumentation.text
        case (SupportData.FindingDocumentation.id, _)                                   => SupportData.FindingDocumentation.text
      }
    }

    deskproConnector.createTicket(DeskproHorizonTicket(
      person = DeskproHorizonTicketPerson(fullName, emailAddress),
      subject = "HMRC Developer Hub: Support Enquiry",
      message = DeskproHorizonTicketMessage.fromRaw(messageContents),
      brand = config.deskproHorizonBrand,
      fields = Map(
        config.deskproHorizonEntryPoint -> deriveEntryPoint()
      ) ++ supportFlow.api.fold(Map.empty[String, String])(v => Map(config.deskproHorizonApiName -> v.name))
    )).flatMap { result =>
      flowRepository.saveFlow(supportFlow.copy(referenceNumber = Some(result.ref), emailAddress = Some(emailAddress)))
    }
  }
}
