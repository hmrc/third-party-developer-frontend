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

package uk.gov.hmrc.modules.submissions.domain.services

trait AskWhenJsonFormatters extends AnswersJsonFormatters {
  import uk.gov.hmrc.modules.submissions.domain.models._
  import play.api.libs.json._
  import uk.gov.hmrc.play.json.Union

  implicit val jsonFormatAskWhenContext = Json.format[AskWhenContext]
  implicit val jsonFormatAskWhenAnswer = Json.format[AskWhenAnswer]
  implicit val jsonFormatAskAlways = Json.format[AlwaysAsk.type]

  implicit val jsonFormatCondition: Format[AskWhen] = Union.from[AskWhen]("askWhen")
    .and[AskWhenContext]("askWhenContext")
    .and[AskWhenAnswer]("askWhenAnswer")
    .and[AlwaysAsk.type]("alwaysAsk")
    .format
}

object AskWhenJsonFormatters extends AskWhenJsonFormatters