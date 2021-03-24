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

import javax.inject.Inject
import play.api.i18n.Messages
import play.api.mvc.Request
import uk.gov.hmrc.govukfrontend.views.viewmodels.footer.FooterItem

class FooterLinks @Inject()(appConfig: FooterConfig) {

  def cookieLink(implicit messages: Messages): FooterItem = FooterItem(
    Some(Messages("footer.links.cookies.text")),
    Some(appConfig.cookies)
  )

  def privacyLink(implicit messages: Messages): FooterItem = FooterItem(
    Some(Messages("footer.links.privacy_policy.text")),
    Some(appConfig.privacy)
  )

  def termsConditionsLink(implicit messages: Messages): FooterItem = FooterItem(
    Some(Messages("footer.links.terms_and_conditions.text")),
    Some(appConfig.termsConditions)
  )

  def govukHelpLink(implicit messages: Messages): FooterItem = FooterItem(
    Some(Messages("footer.links.help_page.text")),
    Some(appConfig.govukHelp)
  )

  def accessibilityLink(implicit messages: Messages, request: Request[_]): FooterItem = FooterItem(
    Some(Messages("footer.links.accessibility.text")),
    Some(appConfig.accessibility)
  )

  def items(implicit messages: Messages, request: Request[_]): Seq[FooterItem] = Seq(
    cookieLink,
    accessibilityLink,
    privacyLink,
    termsConditionsLink,
    govukHelpLink
  )
}
