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

package stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.matchers.should.Matchers

import play.api.http.Status._
import play.api.libs.json.Writes

import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.EncryptedJson

object Stubs extends ApplicationLogger {

  def setupRequest(path: String, status: Int, response: String) = {
    logger.info(s"Stubbing $path with $response")
    stubFor(
      get(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status).withBody(response).withHeader("Content-type", "application/json"))
    )
  }

  def setupDeleteRequest(path: String, status: Int) =
    stubFor(delete(urlEqualTo(path)).willReturn(aResponse().withStatus(status)))

  def setupPostRequest(path: String, status: Int) =
    stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status)))

  def setupPutRequest(path: String, status: Int, response: String) =
    stubFor(
      put(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )

  def setupPostRequest(path: String, status: Int, response: String) =
    stubFor(
      post(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )

  def setupPostContaining(path: String, data: String, status: Int): Unit =
    stubFor(
      post(urlPathEqualTo(path))
        .withRequestBody(containing(data))
        .willReturn(aResponse().withStatus(status))
    )

  def setupEncryptedPostRequest[T](
      path: String,
      data: T,
      status: Int,
      response: String
    )(implicit writes: Writes[T],
      encryptedJson: EncryptedJson
    ) =
    stubFor(
      post(urlPathEqualTo(path))
        .withRequestBody(equalToJson(encryptedJson.toSecretRequestJson(data).toString()))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )
}

object DeskproStub extends Matchers {
  val deskproPath: String         = "/deskpro/ticket"
  val deskproFeedbackPath: String = "/deskpro/feedback"

  def setupTicketCreation(status: Int = OK) = {
    Stubs.setupPostRequest(deskproPath, status)
  }

  def verifyTicketCreationWithSubject(subject: String) = {
    verify(1, postRequestedFor(urlPathEqualTo(deskproPath)).withRequestBody(containing(s""""subject":"$subject"""")))
  }
}

object AuditStub extends Matchers {
  val auditPath: String       = "/write/audit"
  val mergedAuditPath: String = "/write/audit/merged"

  def setupAudit(status: Int = NO_CONTENT, data: Option[String] = None) = {
    if (data.isDefined) {
      Stubs.setupPostContaining(auditPath, data.get, status)
      Stubs.setupPostContaining(mergedAuditPath, data.get, status)
    } else {
      Stubs.setupPostRequest(auditPath, status)
      Stubs.setupPostRequest(mergedAuditPath, status)
    }
  }
}
