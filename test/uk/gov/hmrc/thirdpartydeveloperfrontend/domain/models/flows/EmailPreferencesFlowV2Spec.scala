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

import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.{EmailPreferences, EmailTopic, TaxRegimeInterests}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker

class EmailPreferencesFlowV2Spec extends AnyWordSpec with Matchers with UserBuilder with LocalUserIdTracker {
  val category1                   = "CATEGORY_1"
  val category2                   = "CATEGORY_2"
  val category1Apis               = Set("api1", "api2")
  val category2Apis               = Set("api3", "api2", "api4")
  val emailPreferences            = EmailPreferences(List(TaxRegimeInterests(category1, category1Apis), TaxRegimeInterests(category2, category2Apis)), Set(EmailTopic.TECHNICAL))
  val emailPreferencesWithAllApis = EmailPreferences(List(TaxRegimeInterests(category1, Set.empty)), Set(EmailTopic.TECHNICAL))

  val sessionId = UserSessionId.random

  def developerSession(emailPreferences: EmailPreferences): UserSession = {
    val developer: User = buildTrackedUser(emailPreferences = emailPreferences)
    UserSession(sessionId, LoggedInState.LOGGED_IN, developer)
  }

  def emailPreferencesFlow(selectedCategories: Set[String], selectedAPIs: Map[String, Set[String]], selectedTopics: Set[String]): EmailPreferencesFlowV2 = {
    EmailPreferencesFlowV2(sessionId, selectedCategories, selectedAPIs, selectedTopics, List.empty)
  }

  "EmailPreferencesFlow" when {
    "fromDeveloperSession" should {
      "map developer session to EmailPreferencesFlow object correctly" in {
        val flow = EmailPreferencesFlowV2.fromDeveloperSession(developerSession(emailPreferences))
        flow.selectedCategories should contain allElementsOf (emailPreferences.interests.map(_.regime))
        flow.selectedAPIs.keySet should contain allElementsOf (emailPreferences.interests.map(_.regime))
        flow.selectedAPIs.get(category1).head should contain allElementsOf category1Apis
        flow.selectedAPIs.get(category2).head should contain allElementsOf category2Apis
        flow.selectedTopics should contain allElementsOf Set(EmailTopic.TECHNICAL.toString())
      }

      "map to EmailPreferencesFlow object when ALL APIS in selected Apis" in {
        val flow = EmailPreferencesFlowV2.fromDeveloperSession(developerSession(emailPreferencesWithAllApis))
        flow.selectedCategories should contain allElementsOf (emailPreferencesWithAllApis.interests.map(_.regime))
        flow.selectedAPIs.keySet should contain allElementsOf (emailPreferencesWithAllApis.interests.map(_.regime))
        flow.selectedAPIs.get(category1).head should contain only ("ALL_APIS")
        flow.selectedTopics should contain allElementsOf Set(EmailTopic.TECHNICAL.toString())
      }
    }

    "toEmailPreferences" should {
      "map to email preferences correctly" in {
        val flow              = emailPreferencesFlow(Set(category1, category2), Map(category1 -> category1Apis, category2 -> category2Apis), Set("TECHNICAL"))
        val mappedPreferences = flow.toEmailPreferences

        mappedPreferences shouldBe emailPreferences
      }

      "map to email preferences correctly when ALL_APIS in an api list" in {
        val flow              = emailPreferencesFlow(Set(category1, category2), Map(category1 -> Set("ALL_APIS", "api1")), Set("TECHNICAL"))
        val mappedPreferences = flow.toEmailPreferences

        mappedPreferences shouldBe emailPreferencesWithAllApis
      }
    }
  }
}
