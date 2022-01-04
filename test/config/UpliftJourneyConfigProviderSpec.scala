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

package controllers

import config.UpliftJourneyConfig
import config.On
import config.OnDemand
import config.Off
import play.api.Configuration

class UpliftJourneyConfigSpec extends BaseControllerSpec {

  "UpliftJourneyConfig status" should {

    "throw exception when there is no app config setting present" in {

        val testConfig = Configuration.from(Map("test.key" -> "testval"))

        intercept[RuntimeException]{
          new UpliftJourneyConfig(testConfig).status 
        }.getMessage() contains "No configuration setting found for key 'applicationCheck"

    }

    "be Off when the app config setting is unmatched" in {

        val testConfig = Configuration.from(Map("applicationCheck.canUseNewUpliftJourney" -> "Unmatched"))

        val underTest = new UpliftJourneyConfig(testConfig)  
        
        underTest.status shouldBe Off
    } 

    "be Off when the app config setting is Off" in {

        val testConfig = Configuration.from(Map("applicationCheck.canUseNewUpliftJourney" -> "Off"))

        val underTest = new UpliftJourneyConfig(testConfig)

        underTest.status shouldBe Off
    } 

    "be On when the app config setting is On" in {

      val testConfig = Configuration.from(Map("applicationCheck.canUseNewUpliftJourney" -> "On"))

      val underTest = new UpliftJourneyConfig(testConfig)

      underTest.status shouldBe On
    } 

    "be OnDemand when the app config setting is OnDemand" in {

      val testConfig = Configuration.from(Map("applicationCheck.canUseNewUpliftJourney" -> "OnDemand"))

      val underTest = new UpliftJourneyConfig(testConfig)

      underTest.status shouldBe OnDemand
    } 
  }   
}