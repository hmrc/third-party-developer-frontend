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

package views.include

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.twirl.api.Html
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views.GenericFeedbackBanner
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.include.FeedbackBannerView

class FeedbackBannerViewSpec extends CommonViewSpec with WithCSRFAddToken {
  trait Setup {
    val appConfig: ApplicationConfig = mock[ApplicationConfig]
    val feedbackBannerView: FeedbackBannerView = new FeedbackBannerView(appConfig)
  }

  "Feedback banner view" should {
    "render with the link to the survey from the config" in new Setup {
      val expectedSurveyUrl = "https://example.com/survey"
      when(appConfig.getString(GenericFeedbackBanner.surveyUrlKey)).thenReturn(expectedSurveyUrl)

      val page: Html = feedbackBannerView.render(GenericFeedbackBanner)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      document.select("a").first().attr("href") shouldBe expectedSurveyUrl
    }
  }
}
