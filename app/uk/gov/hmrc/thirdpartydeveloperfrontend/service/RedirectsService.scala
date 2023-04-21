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

import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommands, CommandHandlerTypes, DispatchSuccessResult}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actor
import uk.gov.hmrc.apiplatform.modules.common.domain.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApplicationCommandConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, Standard}

@Singleton
class RedirectsService @Inject() (
    applicationCmdConnector: ApplicationCommandConnector,
    val clock: Clock
  ) extends CommandHandlerTypes[DispatchSuccessResult]
    with ClockNow {

  import RedirectsService._

  private def issueCommand(actor: Actor, application: Application, fn: List[String] => List[String])(implicit hc: HeaderCarrier): Result = {
    val oldRedirectUris = application.access match {
      case Standard(redirectUris, _, _, _, _, _) => redirectUris
      case _                                     => List.empty
    }
    val newRedirectUris = fn(oldRedirectUris)
    val cmd             = ApplicationCommands.UpdateRedirectUris(actor, oldRedirectUris, newRedirectUris, now())
    applicationCmdConnector.dispatch(application.id, cmd, Set.empty)
  }

  def addRedirect(actor: Actor, application: Application, newRedirectUri: String)(implicit hc: HeaderCarrier) = {
    issueCommand(actor, application, addRedirectUris(newRedirectUri))
  }

  def changeRedirect(actor: Actor, application: Application, originalRedirectUri: String, newRedirectUri: String)(implicit hc: HeaderCarrier) = {
    issueCommand(actor, application, changeRedirectUris(originalRedirectUri, newRedirectUri))
  }

  def deleteRedirect(actor: Actor, application: Application, redirectUriToDelete: String)(implicit hc: HeaderCarrier) = {
    issueCommand(actor, application, deleteRedirectUris(redirectUriToDelete))
  }
}

object RedirectsService {

  def addRedirectUris(newRedirectUri: String): List[String] => List[String] = (redirectUris) => {
    (redirectUris ++ List(newRedirectUri)).distinct
  }

  def changeRedirectUris(originalRedirectUri: String, newRedirectUri: String): List[String] => List[String] = (redirectUris) => {
    redirectUris.map {
      case `originalRedirectUri` => newRedirectUri
      case s                     => s
    }
  }

  def deleteRedirectUris(redirectUriToDelete: String): List[String] => List[String] = (redirectUris) => {
    redirectUris.filter(uri => uri != redirectUriToDelete)
  }
}
