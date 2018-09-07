/*
 * Copyright 2018 HM Revenue & Customs
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

package utils

import java.util.UUID

import domain._
import org.joda.time.DateTimeZone
import uk.gov.hmrc.time.DateTimeUtils

import scala.util.Random

trait TestApplications {

  private def randomString(length: Int) = Random.alphanumeric.take(length).mkString

  def aSandboxApplication(appId: String = UUID.randomUUID().toString,
                          clientId: String = randomString(28),
                          adminEmail: String = "admin@example.com",
                          developerEmail: String = "developer@example.com") = {

    anApplication(appId,
      clientId,
      environment = Environment.SANDBOX,
      state = ApplicationState(State.PRODUCTION, None),
      adminEmail = adminEmail,
      developerEmail = developerEmail)
  }

  def anApplication(appId: String = UUID.randomUUID().toString,
                    clientId: String = randomString(28),
                    environment: Environment = Environment.PRODUCTION,
                    state: ApplicationState = ApplicationState.testing,
                    adminEmail: String = "admin@example.com",
                    developerEmail: String = "developer@example.com",
                    access: Access = standardAccess()) = {

    Application(id = appId,
      clientId = clientId,
      name = "App name 1",
      createdOn = DateTimeUtils.now,
      deployedTo = environment,
      description = Some("Description 1"),
      collaborators = Set(Collaborator(adminEmail, Role.ADMINISTRATOR), Collaborator(developerEmail, Role.DEVELOPER)),
      state = state,
      access = access)
  }

  def aStandardApplication(): Application = anApplication()

  def standardAccess(redirectUris: Seq[String] = Seq("https://redirect1", "https://redirect2"),
                     termsAndConditionsUrl: Option[String] = Some("http://example.com/terms"),
                     privacyPolicyUrl: Option[String] = Some("http://example.com/privacy")): Standard = {

    Standard(redirectUris, termsAndConditionsUrl, privacyPolicyUrl)
  }

  def anROPCApplication(): Application = anApplication(access = ropcAccess())

  def ropcAccess(scopes: Set[String] = Set(randomString(10), randomString(10), randomString(10))): Access = ROPC(scopes)

  def aPrivilegedApplication(): Application = anApplication(access = privilegedAccess())

  def privilegedAccess(scopes: Set[String] = Set(randomString(10), randomString(10), randomString(10))): Privileged = Privileged(scopes)

  def tokens(clientId: String = randomString(28),
             clientSecret: String = randomString(28),
             accessToken: String = randomString(28)) = {

    ApplicationTokens(EnvironmentToken(clientId, Seq(aClientSecret(clientSecret)), accessToken))
  }

  private def aClientSecret(secret: String = randomString(28)) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

  implicit class AppAugment(val app: Application) {
    val standardAccess = app.access.asInstanceOf[Standard]

    final def withName(name: String) = app.copy(name = name)

    final def withDescription(description: Option[String]) = app.copy(description = description)

    final def withRedirectUri(redirectUri: String) = app.copy(access = standardAccess.copy(redirectUris = standardAccess.redirectUris :+ redirectUri))

    final def withRedirectUris(redirectUris: Seq[String]) = app.copy(access = standardAccess.copy(redirectUris = redirectUris))

    final def withTermsAndConditionsUrl(url: Option[String]) = app.copy(access = standardAccess.copy(termsAndConditionsUrl = url))

    final def withPrivacyPolicyUrl(url: Option[String]) = app.copy(access = standardAccess.copy(privacyPolicyUrl = url))

    final def withTeamMember(email: String, userRole: Role) = app.copy(collaborators = app.collaborators + Collaborator(email, userRole))

    final def withTeamMembers(teamMembers: Set[Collaborator]) = app.copy(collaborators = teamMembers)

    final def withState(state: ApplicationState) = app.copy(state = state)

    final def withEnvironment(environment: Environment) = app.copy(deployedTo = environment)
  }
}


object TestApplications extends TestApplications
