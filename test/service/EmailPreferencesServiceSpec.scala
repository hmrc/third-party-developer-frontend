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

package service

import connectors.ThirdPartyDeveloperConnector
import utils.AsyncHmrcSpec
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class EmailPreferencesServiceSpec extends AsyncHmrcSpec {

 trait SetUp {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val mockThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
  val underTest = new EmailPreferencesService(mockThirdPartyDeveloperConnector)

 }

  "EmailPreferences" should {
      "return true when connector is called correctly and true" in new SetUp {
          when(mockThirdPartyDeveloperConnector.removeEmailPreferences(*)(*)).thenReturn(Future.successful(true))
          val result = await(underTest.removeEmailPreferences("someEmail"))
          result shouldBe true
      }
  }
  
}
