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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors

import play.api.libs.json._
import play.api.mvc.Request

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._

case class DeskproHorizonTicketMessage(
  message: String
)

case class DeskproHorizonTicket(
  subject: String,
  message: DeskproHorizonTicketMessage,
  brand: Int
)

object DeskproHorizonTicket extends FieldTransformer {
  implicit val ticketMessageFormat: OFormat[DeskproHorizonTicketMessage] = Json.format[DeskproHorizonTicketMessage]
  implicit val ticketFormat: OFormat[DeskproHorizonTicket] = Json.format[DeskproHorizonTicket]

  def fromDeskproTicket(deskproTicket: DeskproTicket): DeskproHorizonTicket = 
    DeskproHorizonTicket(
      subject = deskproTicket.subject,
      message = DeskproHorizonTicketMessage(deskproTicket.message),
      brand = 3
    )

  def createFromSupportEnquiry(supportEnquiry: SupportEnquiryForm, appTitle: String)(implicit request: Request[_]) = {
    val message =
      s"""${supportEnquiry.email} has submitted the following support enquiry:
         |
         |${supportEnquiry.comments}
         |
         |Please send them a response within 2 working days.
         |HMRC Developer Hub""".stripMargin
    DeskproHorizonTicket(
      subject = s"$appTitle: Support Enquiry",
      message = DeskproHorizonTicketMessage(message),
      brand = 3
    )
  }
}
