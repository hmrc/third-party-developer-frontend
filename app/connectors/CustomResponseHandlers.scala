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

package connectors

import play.api.http.Status.BAD_REQUEST
import uk.gov.hmrc.http.{HttpErrorFunctions, HttpReads, HttpResponse}

object CustomResponseHandlers extends HttpErrorFunctions {
  implicit val permissiveBadRequestResponseHandler: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse): HttpResponse = {
      response.status match {
        case BAD_REQUEST => response
        case _ => handleResponse(method, url)(response)
      }
    }
  }
}
