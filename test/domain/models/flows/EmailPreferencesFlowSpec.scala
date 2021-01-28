/*
 * Copyright 2021 HM Revenue & Customs
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

package domain.models.flows

import builder.DeveloperBuilder
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.models.emailpreferences.{EmailPreferences, EmailTopic, TaxRegimeInterests}
import org.scalatest.{Matchers, WordSpec}

class EmailPreferencesFlowSpec extends WordSpec with Matchers with DeveloperBuilder {
    val category1 = "CATEGORY_1"
    val category2 = "CATEGORY_2"
    val category1Apis = Set("api1", "api2")
    val category2Apis =  Set("api3", "api2", "api4")
    val emailPreferences = EmailPreferences(List(TaxRegimeInterests(category1, category1Apis), TaxRegimeInterests(category2, category2Apis)), Set(EmailTopic.TECHNICAL))
    val emailPreferencesWithAllApis = EmailPreferences(List(TaxRegimeInterests(category1, Set.empty)), Set(EmailTopic.TECHNICAL))

    val sessionId = "sessionId"
    
    def developerSession(emailPreferences: EmailPreferences): DeveloperSession = {
        val developer: Developer = buildDeveloper(emailPreferences = emailPreferences)
        val session: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
        DeveloperSession(session)
    }

    def emailPreferencesFlow(selectedCategories: Set[String], selectedAPIs: Map[String, Set[String]], selectedTopics: Set[String]): EmailPreferencesFlow = {
        EmailPreferencesFlow(sessionId, selectedCategories, selectedAPIs, selectedTopics, Seq.empty)
    }

    "EmailPreferencesFlow" when {
        "fromDeveloperSession" should {
            "map developer session to EmailPreferencesFlow object correctly" in {
                val flow = EmailPreferencesFlow.fromDeveloperSession(developerSession(emailPreferences))
                flow.selectedCategories should contain allElementsOf (emailPreferences.interests.map(_.regime))
                flow.selectedAPIs.keySet should contain allElementsOf (emailPreferences.interests.map(_.regime))
                flow.selectedAPIs.get(category1).head should contain allElementsOf category1Apis
                flow.selectedAPIs.get(category2).head should contain allElementsOf category2Apis
                flow.selectedTopics should contain allElementsOf Set(EmailTopic.TECHNICAL.toString())
            }

            "map to EmailPreferencesFlow object when ALL APIS in selected Apis" in {
               val flow = EmailPreferencesFlow.fromDeveloperSession(developerSession(emailPreferencesWithAllApis))
                flow.selectedCategories should contain allElementsOf (emailPreferencesWithAllApis.interests.map(_.regime))
                flow.selectedAPIs.keySet should contain allElementsOf (emailPreferencesWithAllApis.interests.map(_.regime))
                flow.selectedAPIs.get(category1).head should contain only("ALL_APIS")
                flow.selectedTopics should contain allElementsOf Set(EmailTopic.TECHNICAL.toString())
            }
        }

        "toEmailPreferences" should {
            "map to email preferences correctly" in {
              val flow =   emailPreferencesFlow(Set(category1, category2), Map(category1 -> category1Apis, category2 -> category2Apis), Set("TECHNICAL"))
              val mappedPreferences = flow.toEmailPreferences
              
              mappedPreferences shouldBe emailPreferences
            }

           "map to email preferences correctly when ALL_APIS in an api list" in {
              val flow =   emailPreferencesFlow(Set(category1, category2), Map(category1 -> Set("ALL_APIS", "api1")), Set("TECHNICAL"))
              val mappedPreferences = flow.toEmailPreferences
              
              mappedPreferences shouldBe emailPreferencesWithAllApis
            }
        }
    }
}
