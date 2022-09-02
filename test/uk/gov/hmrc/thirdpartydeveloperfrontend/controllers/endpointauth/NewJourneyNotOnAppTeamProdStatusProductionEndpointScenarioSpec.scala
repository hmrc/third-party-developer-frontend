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

class NewJourneyNotOnAppTeamProdStatusProductionEndpointScenarioSpec extends EndpointScenarioSpec
  with IsNewJourneyStandardApplication
  with UserIsNotOnApplicationTeam
  with UserIsAuthenticated
  with AppDeployedToProductionEnvironment
  with AppHasProductionStatus {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/developer/applications/add/:id") => BadRequest()
      case Endpoint("GET",  "/developer/applications/add/production") => BadRequest()
      case Endpoint("GET",  "/developer/applications/add/switch") => BadRequest()
      case Endpoint("POST", "/developer/registration") => BadRequest()
      case Endpoint("GET",  "/developer/registration") => Redirect(s"/developer/applications")
      case Endpoint("GET",  "/developer/reset-password/error") => BadRequest()
      case Endpoint(_, path) if path.contains(":id") || path.contains(":aid") || path.contains(":sid") => NotFound()
      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
