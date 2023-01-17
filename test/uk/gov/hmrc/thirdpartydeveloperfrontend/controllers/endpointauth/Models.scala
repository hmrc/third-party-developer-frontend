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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

import play.api.libs.json.Json

case class Endpoint(verb: String, pathTemplate: String, method: String)

sealed trait Response
case class Success()                  extends Response
case class Redirect(location: String) extends Response
case class BadRequest()               extends Response
case class Locked()                   extends Response
case class Unauthorized()             extends Response
case class Forbidden()                extends Response
case class NotFound()                 extends Response
case class Error(errorMsg: String)    extends Response
case class Unexpected(status: Int)    extends Response

case class RequestValues(endpoint: Endpoint, pathValues: Map[String, String] = Map.empty, queryParams: Map[String, String] = Map.empty, postBody: Map[String, String] = Map.empty) {

  override def toString = {
    var path = endpoint.pathTemplate
    for ((name, value) <- pathValues) {
      path = path.replace(s":$name", value)
    }

    val queryString = queryParams.map(kv => s"${kv._1}=${kv._2}").mkString("&")
    if (queryString.nonEmpty) {
      path += s"?$queryString"
    }

    s"${endpoint.verb} $path" + (postBody.nonEmpty match {
      case true                             => s"\n\twith body ${Json.toJson(postBody)}"
      case false if endpoint.verb == "POST" => "\n\twith no body"
      case false                            => ""
    }) + s"\n\tcalling method ${endpoint.method.replaceAll("(\\S*\\.)*", "")}"
  }
}
