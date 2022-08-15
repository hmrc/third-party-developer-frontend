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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.{CSRFTokenHelper, FakeRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated

import scala.concurrent.Future

class NoAuthEndpointScenarioSpec extends EndpointScenarioSpec {
  when(tpdConnector.findUserId(*)(*)).thenReturn(Future.successful(None))
  when(deskproConnector.createTicket(*)(*)).thenReturn(Future.successful(TicketCreated))

  override def buildRequest(httpVerb: String, requestPath: String): Request[AnyContentAsEmpty.type] =
    CSRFTokenHelper.addCSRFToken(FakeRequest(httpVerb, requestPath))

  override def describeScenario(): String = "User is not authenticated"

  override def expectedEndpointResults(): EndpointResults = EndpointResults(
    Redirect("/developer/login"),
    EndpointResultOverride(Endpoint("GET", "/login"), Success()),
    EndpointResultOverride(Endpoint("GET", "/forgot-password"), Success()),
    EndpointResultOverride(Endpoint("POST", "/support"), BadRequest()),
    EndpointResultOverride(Endpoint("GET", "/reset-password"), Redirect("/developer/reset-password/error")),
    EndpointResultOverride(Endpoint("POST", "/reset-password"), BadRequest()),
    EndpointResultOverride(Endpoint("GET", "/reset-password/error"), BadRequest()),
    EndpointResultOverride(Endpoint("GET", "/resend-confirmation"), Success()),
    EndpointResultOverride(Endpoint("GET", "/login-totp"), Success()),
    EndpointResultOverride(Endpoint("POST", "/login-totp"), BadRequest()),
    EndpointResultOverride(Endpoint("POST", "/registration"), BadRequest()),
    EndpointResultOverride(Endpoint("POST", "/login"), BadRequest()),
    EndpointResultOverride(Endpoint("GET", "/support/submitted"), Success()),
    EndpointResultOverride(Endpoint("GET", "/login/2SV-help"), Success()),
    EndpointResultOverride(Endpoint("POST", "/login/2SV-help"), Success()),
    EndpointResultOverride(Endpoint("GET", "/login/2SV-help/complete"), Success()),
    EndpointResultOverride(Endpoint("GET", "/locked"), Locked()),
    EndpointResultOverride(Endpoint("GET", "/partials/terms-of-use"), Success()),
    EndpointResultOverride(Endpoint("POST", "/forgot-password"), BadRequest()),
    EndpointResultOverride(Endpoint("GET", "/resend-verification"), BadRequest()),
    EndpointResultOverride(Endpoint("GET", "/confirmation"), Success()),
    EndpointResultOverride(Endpoint("GET", "/registration"), Success()),
    EndpointResultOverride(Endpoint("GET", "/user-navlinks"), Success()),
    EndpointResultOverride(Endpoint("GET", "/logout"), Success()),
    EndpointResultOverride(Endpoint("GET", "/support"), Success()),
  )
}
