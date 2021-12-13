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
import domain.models.applications.ApplicationId
import domain.models.apidefinitions.ExtendedApiDefinitionTestDataHelper
import domain.models.connectors.ExtendedApiDefinition
import utils.LocalUserIdTracker
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class NewApplicationEmailPreferencesFlowSpec extends AnyWordSpec with Matchers with ExtendedApiDefinitionTestDataHelper with DeveloperBuilder with LocalUserIdTracker {

    val category1 = "CATEGORY_1"
    val category2 = "CATEGORY_2"
    val category1Apis = Set("api1", "api2")
    val category2Apis =  Set("api3", "api2", "api4")
    val emailPreferences = EmailPreferences(List(TaxRegimeInterests(category1, category1Apis), TaxRegimeInterests(category2, category2Apis)), Set(EmailTopic.TECHNICAL))

    val applicationId = ApplicationId.random
    val sessionId = "sessionId"
    
    def developerSession(emailPreferences: EmailPreferences): DeveloperSession = {
        val developer: Developer = buildDeveloper(emailPreferences = emailPreferences)
        val session: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
        DeveloperSession(session)
    }

    def newApplicationEmailPreferencesFlow(selectedApis: Set[ExtendedApiDefinition], selectedTopics: Set[String]): NewApplicationEmailPreferencesFlow = {
        NewApplicationEmailPreferencesFlow(sessionId, emailPreferences, applicationId, Set.empty, selectedApis, selectedTopics)
    }

    "NewApplicationEmailPreferencesFlow" when {
        "toEmailPreferences" should {
            "map to email preferences correctly" in {
                val newApiInExistingCategory = extendedApiDefinition("new-api", List(category1))
                val newApiInNewCategory = extendedApiDefinition("new-api-2", List("CATEGORY_3"))

                val selectedTopics = Set(EmailTopic.TECHNICAL, EmailTopic.BUSINESS_AND_POLICY)

                val flow =   newApplicationEmailPreferencesFlow(Set(newApiInExistingCategory, newApiInNewCategory), selectedTopics.map(_.toString))
                val mappedPreferences: EmailPreferences = flow.toEmailPreferences
                
                mappedPreferences.interests.length shouldBe 3
                mappedPreferences.interests.find(_.regime == category1).get.services should contain only ("api1", "api2", "new-api")
                mappedPreferences.interests.find(_.regime == category2).get.services should contain only ("api2", "api3", "api4")
                mappedPreferences.interests.find(_.regime == "CATEGORY_3").get.services should contain only ("new-api-2")
                mappedPreferences.topics shouldBe selectedTopics
            }
        }
    }
}
