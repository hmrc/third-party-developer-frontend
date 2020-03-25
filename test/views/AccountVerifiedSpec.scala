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

package views

import config.ApplicationConfig
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.SharedMetricsClearDown
import html.accountVerified

class AccountVerifiedSpec extends UnitSpec with Matchers with MockitoSugar with OneServerPerSuite with SharedMetricsClearDown {

  "Account verified view" should {
    implicit val mockConfig = mock[ApplicationConfig]
    implicit val request = FakeRequest()

    "contain a google analytics event (via the data-journey attribute)" in {
      val mainView = html.accountVerified()
      mainView.body should include("""data-journey="user:registered"""")
    }
  }
}
