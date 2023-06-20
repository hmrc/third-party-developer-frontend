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

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, RedirectUri}
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommand, ApplicationCommands, CommandHandlerTypes, DispatchSuccessResult}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actor
import uk.gov.hmrc.apiplatform.modules.common.domain.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApplicationCommandConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application

@Singleton
class RedirectsService @Inject() (
    applicationCmdDispatcher: ApplicationCommandConnector,
    val clock: Clock
  ) extends CommandHandlerTypes[DispatchSuccessResult]
    with ClockNow {

  private def issueCommand(id: ApplicationId, cmd: ApplicationCommand)(implicit hc: HeaderCarrier): AppCmdResult = {
    applicationCmdDispatcher.dispatch(id, cmd, Set.empty)
  }

  def addRedirect(actor: Actor, application: Application, newRedirectUri: RedirectUri)(implicit hc: HeaderCarrier) = {
    issueCommand(application.id, ApplicationCommands.AddRedirectUri(actor, newRedirectUri, now()))
  }

  def changeRedirect(actor: Actor, application: Application, originalRedirectUri: RedirectUri, newRedirectUri: RedirectUri)(implicit hc: HeaderCarrier) = {
    issueCommand(application.id, ApplicationCommands.ChangeRedirectUri(actor, originalRedirectUri, newRedirectUri, now()))
  }

  def deleteRedirect(actor: Actor, application: Application, redirectUriToDelete: RedirectUri)(implicit hc: HeaderCarrier) = {
    issueCommand(application.id, ApplicationCommands.DeleteRedirectUri(actor, redirectUriToDelete, now()))
  }
}
