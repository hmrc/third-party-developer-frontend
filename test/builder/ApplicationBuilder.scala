/*
 * Copyright 2020 HM Revenue & Customs
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

package builder

import uk.gov.hmrc.time.DateTimeUtils
import java.util.UUID.randomUUID

import domain.models.applications.{ApplicationState, Collaborator, Environment, Role, Standard}
import domain.models.applications.Application
import domain.models.applications.ApplicationId
import domain.models.applications.ClientId

trait ApplicationBuilder {

  def buildApplication(appOwnerEmail: String): Application = {

    val appId = ApplicationId("appid-" + randomUUID.toString)
    val clientId = ClientId("clientid-" + randomUUID.toString)

    Application(
      appId,
      clientId,
      s"$appId-name",
      DateTimeUtils.now,
      DateTimeUtils.now,
      None,
      Environment.SANDBOX,
      Some(s"$appId-description"),
      buildCollaborators(Seq(appOwnerEmail)),
      state = ApplicationState.production(appOwnerEmail, ""),
      access = Standard(
        redirectUris = Seq("https://red1", "https://red2"),
        termsAndConditionsUrl = Some("http://tnc-url.com")
      )
    )
  }

  def buildCollaborators(emails: Seq[String]): Set[Collaborator] = {
    emails.map(email => Collaborator(email, Role.ADMINISTRATOR)).toSet
  }
}
