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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

class NewJourneyDevUserProdStatusProductionEndpointScenarioSpec extends EndpointScenarioSpec
    with IsNewJourneyStandardApplication
    with UserIsDeveloper
    with UserIsAuthenticated
    with AppDeployedToProductionEnvironment
    with AppHasProductionStatus {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET", "/developer/registration", _)                                                  => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/registration", _)                                                 => BadRequest()
      case Endpoint("GET", "/developer/reset-password/error", _)                                          => BadRequest()
      case Endpoint("GET", "/developer/applications/add/production", _)                                   => BadRequest()
      case Endpoint("GET", "/developer/applications/add/switch", _)                                       => BadRequest()
      case Endpoint("GET", "/developer/applications/add/:id", _)                                          => BadRequest()
      case Endpoint("GET", "/developer/applications/:id/add/success", _)                                  => NotFound()
      case Endpoint("POST", "/developer/applications/:id/team-members/remove", _)                         => Forbidden()
      case Endpoint("POST", "/developer/applications/:id/team-members/add", _)                            => Forbidden()
      case Endpoint(_, "/developer/applications/:id/details/terms-of-use", _)                             => Forbidden()
      case Endpoint("GET", "/developer/submissions/application/:aid/terms-of-use-responses", _)           => Forbidden()
      case Endpoint("POST", "/developer/applications/:id/change-subscription", _)                         => BadRequest()
      case Endpoint("POST", "/developer/applications/:id/client-secret-new", _)                           => Forbidden()
      case Endpoint("POST", "/developer/applications/:id/client-secret/:clientSecretId/delete", _)        => Forbidden()
      case Endpoint(_, "/developer/applications/:id/change-private-subscription", _)                      => Forbidden()
      case Endpoint(_, "/developer/applications/:id/change-locked-subscription", _)                       => Forbidden()
      case Endpoint("GET", "/developer/applications/:id/client-id", _)                                    => Forbidden()
      case Endpoint("GET", "/developer/applications/:id/server-token", _)                                 => Forbidden()
      case Endpoint("GET", "/developer/applications/:id/client-secrets", _)                               => Forbidden()
      case Endpoint("GET", "/developer/applications/:id/client-secret/:clientSecretId/delete", _)         => Forbidden()
      case Endpoint("GET", "/developer/applications/:id/push-secrets", _)                                 => Forbidden()
      case Endpoint("GET", "/developer/applications/:id/request-check/appDetails", _)                     => Success()
      case Endpoint("GET", "/developer/applications/:id/delete", _)                                       => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET", "/developer/applications/:id/request-check/submitted", _)                      => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET", "/developer/applications/:id/we-will-check-your-answers", _)                   =>
        Redirect(s"/developer/applications/${applicationId}/sell-resell-or-distribute-your-software")
      case Endpoint("POST", "/developer/applications/:id/sell-resell-or-distribute-your-software", _)     =>
        Redirect(s"/developer/submissions/application/${applicationId}/production-credentials-checklist")
      case Endpoint(_, "/developer/submissions/application/:aid/production-credentials-checklist", _)     => BadRequest() // must be in 'testing' state
      case Endpoint(_, "/developer/submissions/application/:aid/cancel-request", _)                       => BadRequest() // must not be in production state
      case Endpoint("GET", "/developer/submissions/application/:aid/check-answers", _)                    => BadRequest() // must be in testing state
      case Endpoint("GET", "/developer/submissions/application/:aid/request-received", _)                 => BadRequest()
      case Endpoint("GET", "/developer/submissions/application/:aid/submit-request", _)                   => BadRequest() // must be in testing state
      case Endpoint(_, "/developer/submissions/application/:aid/start-using-your-application", _)         => Forbidden()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/details/change")          => Forbidden()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/delete")                  => Forbidden()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/request-check")           => BadRequest()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/check-your-answers")      => BadRequest()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/responsible-individual/") => Forbidden()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/ip-allowlist/")           => Forbidden()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/redirect-uris/")          => Forbidden()

      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
