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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors

import controllers.SignOutSurveyForm
import play.api.libs.json.Json
import play.api.mvc.Request
import play.mvc.Http.HeaderNames.REFERER
import uk.gov.hmrc.http.HeaderCarrier


case class Feedback(name: String,
                    email: String,
                    subject: String,
                    rating: String,
                    message: String,
                    referrer: String,
                    javascriptEnabled: String,
                    userAgent: String,
                    authId: String,
                    areaOfTax: String,
                    sessionId: String,
                    service: Option[String])


object Feedback extends FieldTransformer {

  implicit val formats = Json.format[Feedback]

  def create(name: String,
             email: String,
             rating: String,
             subject: String,
             message: String,
             referrer: String,
             isJavascript: Boolean,
             hc: HeaderCarrier,
             request: Request[AnyRef],
             service: Option[String]) =
    Feedback(
      name.trim,
      email,
      subject,
      rating,
      trimmedMessageFrom(message),
      referrer,
      ynValueOf(isJavascript),
      userAgentOf(request),
      userIdFrom(request, hc),
      areaOfTaxOf,
      sessionIdFrom(hc),
      service)

  def createFromSurvey(survey: SignOutSurveyForm, title: Option[String])(implicit hc: HeaderCarrier, request: Request[AnyRef]): Feedback = {

    Feedback.create(survey.name, survey.email, survey.rating.map(_.toString).getOrElse("-"),
      "Beta feedback submission", survey.improvementSuggestions, request.headers.get(REFERER).getOrElse(""),
      survey.isJavascript, hc, request, title)
  }
}


trait FieldTransformer {
  val NA = "n/a"
  val UNKNOWN = "unknown"

  def sessionIdFrom(hc: HeaderCarrier) = hc.sessionId.map(_.value).getOrElse(NA)

  def areaOfTaxOf = UNKNOWN

  def userIdFrom(request: Request[AnyRef], hc: HeaderCarrier): String = NA  // TODO - userId is no longer held on header carrier

  def userAgentOf(request: Request[AnyRef]) = request.headers.get("User-Agent").getOrElse(NA)

  def ynValueOf(javascript: Boolean) = if (javascript) "Y" else "N"

  def trimmedMessageFrom(text: String) = {
    val trimmed = text.trim()
    if (trimmed.isEmpty) NA else trimmed
  }
}

object TicketId {
  implicit val formats = Json.format[TicketId]
}

case class TicketId(ticket_id: Int)
