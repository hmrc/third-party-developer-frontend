/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import play.api.mvc.{Call, RequestHeader}
import play.filters.csrf.CSRF._

object SpliceCSRFToken {
  // Not materially different from play's play.filters.csrf.CSRF helper, but rather than
  // appending the token to the end of the query args, inserts it as the first query arg.
  def apply(call: Call)(implicit rh: RequestHeader): Call = {
    val token = getToken(rh).getOrElse { sys.error("No CSRF token present!") }
    val url = if (call.url.contains("?")) call.url.replace("?", s"?${token.name}=${token.value}&")
              else s"${call.url}?${token.name}=${token.value}"

    call.copy(url = url)
  }

//  private def getToken(implicit request: RequestHeader): Option[Token] = {
//    // Try to get the re-signed token first, then get the "new" token.
//    for {
//      name <- request.attrs(Attrs.NameRequestTag)
//      value <- request.tags.get(ReSignedRequestTag) orElse request.attrs(Attrs.RequestTag)
//    } yield Token(name, value)
//  }


//  object Attrs {
//    val NameRequestTag: TypedKey[String] = TypedKey("CSRF_TOKEN_NAME")
//    val ReSignedRequestTag: TypedKey[String] = TypedKey("CSRF_TOKEN_RE_SIGNED")
//    val RequestTag: TypedKey[String] = TypedKey("CSRF_TOKEN_RE_SIGNED")
//  }
  // Getting an attribute from a Request or RequestHeader
//  val userName: String = request.attrs(Attrs.NameRequestTag)
//  val optUserName: [String] = req.attrs.get(Attrs.UserName)
  // Setting an attribute on a Request or RequestHeader
//  val newReq = req.addAttr(Attrs.UserName, newName)
}
