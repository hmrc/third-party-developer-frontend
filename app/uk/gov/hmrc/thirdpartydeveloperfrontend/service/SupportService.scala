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

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.common.services.{ApplicationLogger, EitherTHelper}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, DeskproHorizonConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support.{ApplyForPrivateApiAccessForm, SupportData, SupportDetailsForm}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproHorizonTicket, DeskproHorizonTicketMessage, DeskproHorizonTicketPerson}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
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
      case None       => SupportFlow(sessionId, "unknown", None)
    }
  }

  def getSupportFlow(sessionId: String): Future[SupportFlow] = {
    for {
      flow      <- fetchSupportFlow(sessionId)
      savedFlow <- flowRepository.saveFlow(flow)
    } yield savedFlow
  }

  def updateWithDelta(fn: SupportFlow => SupportFlow)(flow: SupportFlow): Future[SupportFlow] = {
    flowRepository.saveFlow(fn(flow))
  }

  def setPrivateApiChoice(sessionId: String, apiChoice: String): Future[Either[Throwable, SupportFlow]] = {
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
    val deskproTicket = buildTicket(
      supportFlow,
      form.fullName,
      form.emailAddress,
      form.details
    )

    submitTicket(
      supportFlow,
      deskproTicket.copy(
        fields =
          deskproTicket.fields
            ++ form.organisation.fold(Map.empty[String, String])(v => Map(config.deskproHorizonOrganisation -> v))
            ++ form.teamMemberEmailAddress.fold(Map.empty[String, String])(v => Map(config.deskproHorizonTeamMemberEmail -> v))
      )
    )
  }

  def submitTicket(supportFlow: SupportFlow, form: ApplyForPrivateApiAccessForm)(implicit hc: HeaderCarrier): Future[SupportFlow] = {
    submitTicket(
      supportFlow,
      buildTicket(
        supportFlow,
        form.fullName,
        form.emailAddress,
        s"Private API documentation access request for Application Id[${form.applicationId}] to ${supportFlow.privateApi.getOrElse("?")} API."
      )
    )
  }

  private def buildTicket(supportFlow: SupportFlow, fullName: String, emailAddress: String, messageContents: String): DeskproHorizonTicket = {
    // Entry point is currently the value of the text on the radio button but may not always be so.
    def deriveSupportReason(): String = {
      (supportFlow.entrySelection, supportFlow.subSelection) match {
        case (SupportData.FindingAnApi.id, _)                                          => SupportData.FindingAnApi.text
        case (SupportData.UsingAnApi.id, Some(SupportData.MakingAnApiCall.id))         => SupportData.MakingAnApiCall.text
        case (SupportData.UsingAnApi.id, Some(SupportData.GettingExamples.id))         => SupportData.GettingExamples.text
        case (SupportData.UsingAnApi.id, Some(SupportData.ReportingDocumentation.id))  => SupportData.ReportingDocumentation.text
        case (SupportData.UsingAnApi.id, Some(SupportData.PrivateApiDocumentation.id)) => SupportData.PrivateApiDocumentation.text
        case (SupportData.PrivateApiDocumentation.id, _)                               => SupportData.PrivateApiDocumentation.text // TODO - fix
        case (SupportData.SigningIn.id, _)                                             => SupportData.SigningIn.text
        case (SupportData.SettingUpApplication.id, _)                                  => SupportData.SettingUpApplication.text
        case (SupportData.ReportingDocumentation.id, _)                                => SupportData.ReportingDocumentation.text
        case (SupportData.NoneOfTheAbove.id, _)                                        => SupportData.NoneOfTheAbove.text
      }
    }

    DeskproHorizonTicket(
      person = DeskproHorizonTicketPerson(fullName, emailAddress),
      subject = "HMRC Developer Hub: Support Enquiry",
      message = DeskproHorizonTicketMessage.fromRaw(messageContents),
      brand = config.deskproHorizonBrand,
      fields = Map(
        config.deskproHorizonSupportReason -> deriveSupportReason()
      ) ++ supportFlow.api.fold(Map.empty[String, String])(v => Map(config.deskproHorizonApiName -> v))
    )
  }

  private def submitTicket(supportFlow: SupportFlow, ticket: DeskproHorizonTicket)(implicit hc: HeaderCarrier): Future[SupportFlow] = {
    deskproConnector.createTicket(
      ticket
    ).flatMap { result =>
      flowRepository.saveFlow(supportFlow.copy(referenceNumber = Some(result.ref), emailAddress = Some(ticket.person.email)))
    }
  }
}
