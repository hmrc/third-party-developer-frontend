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

import play.api.mvc.{MessagesControllerComponents, Request}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.ExtendedDevHubAuthorization

abstract class LoggedOutController(mcc: MessagesControllerComponents) extends TpdfeBaseController(mcc) with ExtendedDevHubAuthorization {

  implicit def developerSessionFromRequest(implicit request: UserRequest[_]): UserSession = request.userSession

  implicit def hc(implicit request: Request[_]): HeaderCarrier = {
    val carrier = super.hc
    request match {
      case x: MaybeUserRequest[_] => enrichHeaders(carrier, x.userSession)
      case x: UserRequest[_]      => enrichHeaders(carrier, x.userSession)
      case _                      => carrier
    }
  }
}
