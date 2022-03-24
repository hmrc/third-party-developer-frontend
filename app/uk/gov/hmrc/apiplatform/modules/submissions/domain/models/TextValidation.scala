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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.models

import org.apache.commons.validator.routines.EmailValidator
import play.api.libs.json.Json
import scala.util.Try
import scala.util.Success

sealed trait TextValidation {
  def isValid(text: String): Boolean = this match {
    case TextValidation.Url => Try(new java.net.URL(text)) match { 
      case Success(_) => true
      case _ => false
    }
    case TextValidation.Email => TextValidation.emailValidator.isValid(text)
    case TextValidation.MatchRegex(regex) => {
      val matcher = regex.r
      text match {
        case matcher(_*) => true
        case _ => false
      }
    }
  }
}

object TextValidation {
  val emailValidator = EmailValidator.getInstance()

  case object Url extends TextValidation
  case class MatchRegex(regex: String) extends TextValidation
  case object Email extends TextValidation

  import uk.gov.hmrc.play.json.Union

  implicit val formatAsUrl = Json.format[Url.type]
  implicit val formatMatchRegex = Json.format[MatchRegex]
  implicit val formatIsEmail = Json.format[Email.type]

  implicit val formatTextValidation = Union.from[TextValidation]("validationType")
    .and[Url.type]("url")
    .and[MatchRegex]("regex")
    .and[Email.type]("email")
    .format
}