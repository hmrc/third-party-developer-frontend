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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.ExtendedDevHubAuthorization
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.http.HeaderCarrier
import play.api.mvc.{Request, Headers}

trait HeaderEnricher {
  def enrichHeaders(hc: HeaderCarrier, user: Option[DeveloperSession]): HeaderCarrier =
    user match {
      case Some(dev) => enrichHeaders(hc, dev)
      case _         => hc
    }

  def enrichHeaders(hc: HeaderCarrier, user: DeveloperSession): HeaderCarrier =
    hc.withExtraHeaders("X-email-address" -> user.email, "X-name" -> user.displayedNameEncoded)

  implicit class RequestWithAjaxSupport(h: Headers) {
    def isAjaxRequest: Boolean = h.get("X-Requested-With").contains("XMLHttpRequest")
  }
}

abstract class LoggedOutController(mcc: MessagesControllerComponents) extends BaseController(mcc) with ExtendedDevHubAuthorization {

  implicit def developerSessionFromRequest(implicit request: UserRequest[_]): DeveloperSession = request.developerSession

  implicit def hc(implicit request: Request[_]): HeaderCarrier = {
    val carrier = super.hc
    request match {
      case x: MaybeUserRequest[_] => enrichHeaders(carrier, x.developerSession)
      case x: UserRequest[_]      => enrichHeaders(carrier, x.developerSession)
      case _                      => carrier
    }
  }
}
