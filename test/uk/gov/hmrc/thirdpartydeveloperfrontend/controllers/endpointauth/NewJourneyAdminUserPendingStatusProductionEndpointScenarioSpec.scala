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

class NewJourneyAdminUserPendingStatusProductionEndpointScenarioSpec extends EndpointScenarioSpec
  with IsNewJourneyStandardApplication
  with UserIsAdmin
  with UserIsAuthenticated
  with AppDeployedToProductionEnvironment
  with AppHasPendingGatekeeperApprovalStatus {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/developer/applications/:id/add/subscriptions", _) => Success()
      case Endpoint("GET",  "/developer/applications/:id/add/success", _) => NotFound()
      case Endpoint("GET",  "/developer/applications/:id/api-metadata", _) => BadRequest()
      case Endpoint("POST", "/developer/applications/:id/change-subscription", _) => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/client-id", _) => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/credentials", _) => NotFound()
      case Endpoint("GET",  "/developer/applications/:id/details", _) => Redirect(s"/developer/submissions/application/${applicationId.value}/view-answers")
      case Endpoint(_,      "/developer/applications/:id/details/change", _) => Forbidden()
      case Endpoint(_,      "/developer/applications/:id/details/terms-of-use", _) => NotFound()
      case Endpoint("GET",  "/developer/applications/:id/request-check/submitted", _) => Success()
      case Endpoint("GET",  "/developer/applications/:id/request-check/appDetails", _) => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/applications/:id/server-token", _) => BadRequest()
      case Endpoint("POST", "/developer/registration", _) => BadRequest()
      case Endpoint("GET",  "/developer/registration", _) => Redirect("/developer/applications")
      case Endpoint("GET",  "/developer/reset-password/error", _) => BadRequest()
      case Endpoint("GET",  "/developer/submissions/application/:aid/check-answers", _) => BadRequest()
      case Endpoint(_,      "/developer/submissions/application/:aid/production-credentials-checklist", _) => BadRequest()
      case Endpoint(_,      "/developer/submissions/application/:aid/start-using-your-application", _) => NotFound()
      case Endpoint("GET",  "/developer/submissions/application/:aid/submit-request", _) => BadRequest()
      case Endpoint("GET",  "/developer/submissions/application/:aid/terms-of-use-responses", _) => NotFound()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/add/subscription") => BadRequest()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/api-metadata") => BadRequest()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/check-your-answers") => BadRequest()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/client-secret") => BadRequest()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/delete") => NotFound()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/details/change") => NotFound()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/ip-allowlist") => NotFound()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/redirect-uris") => NotFound()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/request-check") => BadRequest()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/responsible-individual") => NotFound()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/team-members") => NotFound()

      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
