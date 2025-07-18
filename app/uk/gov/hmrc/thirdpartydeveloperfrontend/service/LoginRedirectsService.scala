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

import java.time.Clock
import javax.inject._

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, LoginRedirectUri}
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommands, CommandHandlerTypes, DispatchSuccessResult, RedirectCommand}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actor, ApplicationId}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnectorCommandModule

@Singleton
class LoginRedirectsService @Inject() (
    apmCmdModule: ApmConnectorCommandModule,
    val clock: Clock
  ) extends CommandHandlerTypes[DispatchSuccessResult]
    with ClockNow {

  private def issueCommand(id: ApplicationId, cmd: RedirectCommand)(implicit hc: HeaderCarrier): AppCmdResult = {
    apmCmdModule.dispatch(id, cmd, Set.empty)
  }

  def addLoginRedirect(actor: Actor, application: ApplicationWithCollaborators, newRedirectUri: LoginRedirectUri)(implicit hc: HeaderCarrier) = {
    issueCommand(application.id, ApplicationCommands.AddLoginRedirectUri(actor, newRedirectUri, instant()))
  }

  def changeLoginRedirect(
      actor: Actor,
      application: ApplicationWithCollaborators,
      originalRedirectUri: LoginRedirectUri,
      newRedirectUri: LoginRedirectUri
    )(implicit hc: HeaderCarrier
    ) = {
    issueCommand(application.id, ApplicationCommands.ChangeLoginRedirectUri(actor, originalRedirectUri, newRedirectUri, instant()))
  }

  def deleteLoginRedirect(actor: Actor, application: ApplicationWithCollaborators, redirectUriToDelete: LoginRedirectUri)(implicit hc: HeaderCarrier) = {
    issueCommand(application.id, ApplicationCommands.DeleteLoginRedirectUri(actor, redirectUriToDelete, instant()))
  }
}
