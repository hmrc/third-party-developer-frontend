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

class AdminOnProductionAppEndpointScenarioSpec extends EndpointScenarioSpec
  with IsNewJourneyStandardApplication
  with UserIsAdmin
  with UserIsAuthenticated
  with AppDeployedToProductionEnvironment
  with AppHasProductionStatus {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/developer/registration") => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/registration") => BadRequest()
      case Endpoint("GET",  "/developer/reset-password/error") => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/add/success") => NotFound()
      case Endpoint(_,      "/developer/applications/:id/details/change") => Forbidden()
      case Endpoint("POST", "/developer/applications/:id/change-subscription") => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/request-check/appDetails") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/applications/:id/request-check/submitted") => getEndpointSuccessResponse(endpoint)
      case Endpoint("POST", "/developer/applications/:id/delete-subordinate") => Error("uk.gov.hmrc.http.ForbiddenException: Only standard subordinate applications can be deleted by admins")
      case Endpoint(_,      "/developer/submissions/application/:aid/production-credentials-checklist") => BadRequest() // must be in 'testing' state
      case Endpoint(_,      "/developer/submissions/application/:aid/cancel-request") => BadRequest() // must not be in production state
      case Endpoint("GET",  "/developer/submissions/application/:aid/check-answers") => BadRequest() // must be in testing state
      case Endpoint("GET",  "/developer/submissions/application/:aid/view-answers") => BadRequest() // must not be in pending approval state
      case Endpoint("GET",  "/developer/submissions/application/:aid/submit-request") => BadRequest() // must be in testing state
      case Endpoint(_,      "/developer/submissions/application/:aid/start-using-your-application") => NotFound() // must be in pre-production state
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/request-check") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/check-your-answers") => BadRequest()

      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
