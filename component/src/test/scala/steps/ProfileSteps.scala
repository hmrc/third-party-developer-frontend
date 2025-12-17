/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.http.Status._

import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.UpdateRequest

object ProfileSteps {

  // ^I want to successfully change my profile$
  def givenIWantToSuccessfullyChangeMyProfile(): Unit = {
    // Pulling the user id from the developer in the test context defined in LoginSteps
    val userId = TestContext.developer.userId
    DeveloperStub.update(userId, UpdateRequest("Joe", "Bloggs"), OK)
  }

  // ^I want to successfully change my password
  def givenIWantToSuccessfullyChangeMyPassword(): Unit = {
    Stubs.setupPostRequest("/change-password", NO_CONTENT)
  }

}
