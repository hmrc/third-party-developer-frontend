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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiCategory
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.{EmailPreferences, EmailTopic, TaxRegimeInterests}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.CombinedApiTestDataHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.session.DeveloperSession

class NewApplicationEmailPreferencesFlowV2V2Spec extends AnyWordSpec with Matchers with CombinedApiTestDataHelper with UserBuilder with LocalUserIdTracker {

  val category1     = ApiCategory.AGENTS
  val category2     = ApiCategory.BUSINESS_RATES
  val category3     = ApiCategory.CHARITIES
  val category1Apis = Set("api1", "api2")
  val category2Apis = Set("api3", "api2", "api4")

  val emailPreferences =
    EmailPreferences(List(TaxRegimeInterests(category1.toString, category1Apis), TaxRegimeInterests(category2.toString, category2Apis)), Set(EmailTopic.TECHNICAL))

  val applicationId = ApplicationId.random
  val sessionId     = UserSessionId.random

  def developerSession(emailPreferences: EmailPreferences): DeveloperSession = {
    val developer: User      = buildTrackedUser(emailPreferences = emailPreferences)
    val session: UserSession = UserSession(sessionId, LoggedInState.LOGGED_IN, developer)
    DeveloperSession(session)
  }

  def newApplicationEmailPreferencesFlow(selectedApis: Set[CombinedApi], selectedTopics: Set[String]): NewApplicationEmailPreferencesFlowV2 = {
    NewApplicationEmailPreferencesFlowV2(sessionId, emailPreferences, applicationId, Set.empty, selectedApis, selectedTopics)
  }

  "NewApplicationEmailPreferencesFlow" when {
    "toEmailPreferences" should {
      "map to email preferences correctly" in {
        val newApiInExistingCategory = combinedApi("new-api", List(category1))
        val newApiInNewCategory      = combinedApi("new-api-2", List(category3))

        val selectedTopics = Set(EmailTopic.TECHNICAL, EmailTopic.BUSINESS_AND_POLICY)

        val flow                                = newApplicationEmailPreferencesFlow(Set(newApiInExistingCategory, newApiInNewCategory), selectedTopics.map(_.toString))
        val mappedPreferences: EmailPreferences = flow.toEmailPreferences

        mappedPreferences.interests.length shouldBe 3
        mappedPreferences.interests.find(category1.toString == _.regime).get.services should contain theSameElementsAs List("api1", "api2", "new-api")
        mappedPreferences.interests.find(category2.toString == _.regime).get.services should contain theSameElementsAs List("api2", "api3", "api4")
        mappedPreferences.interests.find(category3.toString == _.regime).get.services should contain only ("new-api-2")
        mappedPreferences.topics shouldBe selectedTopics
      }
    }
  }
}
