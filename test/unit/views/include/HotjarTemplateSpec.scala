/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.views.include

import config.ApplicationConfig
import domain._
import junit.framework.TestCase
import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.i18n.Messages
import play.api.mvc.Request
import uk.gov.hmrc.time.DateTimeUtils

class HotjarTemplateSpec extends PlaySpec with MockitoSugar with OneAppPerSuite {

  val applicationId = "applicationId"
  val mockRequest = mock[Request[Any]]
  val mockApplicationConfig = mock[ApplicationConfig]
  val mockMessages = mock[Messages]
  val developer = Developer("Test", "Test", "Test", None)

  val application =
          Application(
          "APPLICATION_ID",
          "CLIENT_ID",
          "APPLICATION NAME",
          DateTimeUtils.now,
          Environment.PRODUCTION,
          Some("APPLICATION DESCRIPTION"),
          Set(Collaborator("sample@example.com", Role.ADMINISTRATOR), Collaborator("someone@example.com", Role.DEVELOPER)),
          Standard(),
          true,
          ApplicationState(State.TESTING, None, None, DateTimeUtils.now)
        )

  "nameSubmittedPage" must {

    "render hotjar script when hotjar id is defined and hotjar feature is enabled" in new TestCase {
      given(mockApplicationConfig.hotjarEnabled) willReturn true
      given(mockApplicationConfig.hotjarId) willReturn 123

      val renderedHtml = views.html.editapplication.nameSubmitted.render(applicationId, application, mockRequest, developer, mockMessages, mockApplicationConfig, "credentials")
      renderedHtml.body must include("hotjar")
      renderedHtml.body must include("hjid:123")
    }

    "render without hotjar script when hotjar id is not and hotjar feature is disabled" in new TestCase {
      given(mockApplicationConfig.hotjarEnabled) willReturn false

      val renderedHtml = views.html.editapplication.nameSubmitted.render(applicationId, application, mockRequest, developer, mockMessages, mockApplicationConfig, "credentials")
      renderedHtml.body must not include("hotjar")
      renderedHtml.body must not include("hjid:123")
    }

    "render without hotjar script when hotjar id is defined and hotjar feature is disabled" in new TestCase {
      given(mockApplicationConfig.hotjarEnabled) willReturn false

      val renderedHtml = views.html.editapplication.nameSubmitted.render(applicationId, application, mockRequest, developer, mockMessages, mockApplicationConfig, "credentials")
      renderedHtml.body must not include("hotjar")
      renderedHtml.body must not include("hjid:123")
    }
  }

  "submittedForCheckPage" must {

    "render without hotjar script when hotjar id is defined and hotjar feature is enabled" in new TestCase {
      given(mockApplicationConfig.hotjarEnabled) willReturn true
      given(mockApplicationConfig.hotjarId) willReturn 123

      val renderedHtml = views.html.editapplication.credentialsPartials.submittedForCheck.render(applicationId)
      renderedHtml.body must not include ("hotjar")
      renderedHtml.body must not include ("hjid:123")
    }
  }
}

