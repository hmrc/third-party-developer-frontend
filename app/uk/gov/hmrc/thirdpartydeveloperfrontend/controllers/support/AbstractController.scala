/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

import play.api.mvc.{AnyContent, MessagesControllerComponents}
import play.api.data.{Form}

import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession

abstract class AbstractController(
    mcc: MessagesControllerComponents,
 )
 extends BaseController(mcc) with SupportCookie {

  val supportForm: Form[SupportEnquiryForm] = SupportEnquiryForm.form

  protected def fullyloggedInDeveloper(implicit request: MaybeUserRequest[AnyContent]): Option[DeveloperSession] =
    request.developerSession.filter(_.loggedInState.isLoggedIn)

}
