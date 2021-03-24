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

package views.templates

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.Request
import views.html.helper

@Singleton
class FooterConfig @Inject()(config: Configuration) {

  private lazy val urlFooterConfig = config.underlying.getConfig("urls.footer")

  lazy val cookies: String         = urlFooterConfig.getString("cookies")
  lazy val privacy: String         = urlFooterConfig.getString("privacy")
  lazy val termsConditions: String = urlFooterConfig.getString("termsConditions")
  lazy val govukHelp: String       = urlFooterConfig.getString("govukHelp")

  def accessibility(implicit request: Request[_]): String =
    s"${urlFooterConfig.getString("accessibility")}/hmrc-developer-hub?referrerUrl=${helper.urlEncode(request.uri)}"
}
