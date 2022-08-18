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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

case class Endpoint(verb: String, pathTemplate: String)

sealed trait Response
case class Success() extends Response
case class Redirect(location: String) extends Response
case class BadRequest() extends Response
case class Locked() extends Response
case class Unauthorized() extends Response
case class Error(errorMsg: String) extends Response
case class Unexpected(status: Int) extends Response

case class ExpectedResponseOverride(endpoint: Endpoint, expectedResponse: Response)
case class ExpectedResponses(defaultResponse: Response, responseOverrides: ExpectedResponseOverride*)

case class RequestValues(endpoint: Endpoint, pathValues: Map[String,String] = Map.empty, queryParams: Map[String,String] = Map.empty, postBody: Option[Map[String,String]] = None) {
  override def toString() = {
    var path = endpoint.pathTemplate
    for((name,value) <- pathValues) {
      path = path.replace(s":${name}", value)
    }

    val queryString = queryParams.map(kv => s"${kv._1}=${kv._2}").mkString("&")
    if (!queryString.isEmpty) {
      path += s"?${queryString}"
    }

    s"${endpoint.verb} $path" + (postBody match {
      case Some(values) => s" with body ${values}"
      case None => ""
    })
  }
}