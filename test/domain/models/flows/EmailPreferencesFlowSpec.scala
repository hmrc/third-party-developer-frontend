/*
 * Copyright 2020 HM Revenue & Customs
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

import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.models.emailpreferences.{EmailPreferences, EmailTopic, TaxRegimeInterests}
import org.scalatest.{Matchers, WordSpec}

class EmailPreferencesFlowSpec extends WordSpec with Matchers {

    val category1 = "CATEGORY_1"
    val category2 = "CATEGORY_2"
    val category1Apis = Set("api1", "api2")
    val category2Apis =  Set("api3", "api2", "api4")
    val emailPreferences = EmailPreferences(List(TaxRegimeInterests(category1, category1Apis), TaxRegimeInterests(category2, category2Apis)), Set(EmailTopic.TECHNICAL))
    val developer: Developer = Developer("third.party.developer@example.com", "John", "Doe")
    val developerWithEmailPrefences: Developer = developer.copy(emailPreferences = emailPreferences)
    val sessionId = "sessionId"
    val session: Session = Session(sessionId, developerWithEmailPrefences, LoggedInState.LOGGED_IN)
    val sessionNoEMailPrefences: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val loggedInDeveloper: DeveloperSession = DeveloperSession(session)
    
    "EmailPreferencesFlow" when {

        "fromDeveloperSession" should {
            "map developer session to EmailPreferencesFlow object correctly" in {
                val flow = EmailPreferencesFlow.fromDeveloperSession(loggedInDeveloper)
                flow.selectedCategories should contain allElementsOf (emailPreferences.interests.map(_.regime))
                flow.selectedAPIs.keySet should contain allElementsOf (emailPreferences.interests.map(_.regime))
                flow.selectedAPIs.get(category1).head should contain allElementsOf category1Apis
                flow.selectedAPIs.get(category2).head should contain allElementsOf category2Apis
                flow.selectedTopics should contain allElementsOf Set(EmailTopic.TECHNICAL.toString())
            }
        }
    }
  
}
