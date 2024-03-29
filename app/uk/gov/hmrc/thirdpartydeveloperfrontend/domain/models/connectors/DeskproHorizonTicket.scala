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

import scala.util.Properties

import play.api.libs.json._

case class DeskproHorizonTicketPerson(
    name: String,
    email: String
  )

case class DeskproHorizonTicketMessage(
    message: String,
    format: String = "html"
  )

object DeskproHorizonTicketMessage {
  def fromRaw(message: String): DeskproHorizonTicketMessage = DeskproHorizonTicketMessage(message.replaceAll(Properties.lineSeparator, "<br>"))

}

case class DeskproHorizonTicket(
    person: DeskproHorizonTicketPerson,
    subject: String,
    message: DeskproHorizonTicketMessage,
    brand: Int,
    fields: Map[String, String] = Map.empty
  )

object DeskproHorizonTicket extends FieldTransformer {
  implicit val ticketPersonFormat: OFormat[DeskproHorizonTicketPerson]   = Json.format[DeskproHorizonTicketPerson]
  implicit val ticketMessageFormat: OFormat[DeskproHorizonTicketMessage] = Json.format[DeskproHorizonTicketMessage]
  implicit val ticketFormat: OFormat[DeskproHorizonTicket]               = Json.format[DeskproHorizonTicket]

  def fromDeskproTicket(deskproTicket: DeskproTicket, brand: Int): DeskproHorizonTicket =
    DeskproHorizonTicket(
      person = DeskproHorizonTicketPerson(deskproTicket.name, deskproTicket.email.text),
      subject = deskproTicket.subject,
      brand = brand,
      message = DeskproHorizonTicketMessage.fromRaw(deskproTicket.message)
    )
}
