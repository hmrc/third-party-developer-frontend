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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import play.api.libs.json.{JsSuccess, Json}

import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.FieldName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.SubscriptionFieldsConnectorDomain.{ApiFieldDefinitions, FieldDefinition}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessRequirement.NoOne
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{AccessRequirements, DevhubAccessRequirements}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class ApiFieldDefinitionsSpec extends AsyncHmrcSpec {

  private def basicFieldDefinitionJson =
    """{
      |    "apiContext": "my-context",
      |    "apiVersion": "1.0",
      |    "fieldDefinitions": [
      |        {
      |            "name": "field-name",
      |            "description": "my-description",
      |            "shortDescription": "my-shortDescription",
      |            "hint": "my-hint",
      |            "type": "STRING",
      |            "access": {}
      |        }
      |    ]
      |}""".stripMargin

  private val basicFieldDefinition: ApiFieldDefinitions = {
    ApiFieldDefinitions(
      ApiContext("my-context"),
      ApiVersionNbr("1.0"),
      List(FieldDefinition(FieldName("field-name"), "my-description", "my-shortDescription", "my-hint", "STRING", AccessRequirements.Default))
    )
  }

  private def fieldDefinitionWithAccessJson =
    """{
      |    "apiContext": "my-context",
      |    "apiVersion": "1.0",
      |    "fieldDefinitions": [
      |        {
      |            "name": "field-name",
      |            "description": "my-description",
      |            "shortDescription": "my-shortDescription",
      |            "hint": "my-hint",
      |            "type": "STRING",
      |            "access": {
      |                "devhub": {
      |                    "read": "noOne",
      |                    "write": "noOne"
      |                }
      |            }
      |        }
      |    ]
      |}""".stripMargin

  "from json" should {
    import SubscriptionFieldsConnectorJsonFormatters._
    "for basic field definition" in {
      Json.fromJson[ApiFieldDefinitions](Json.parse(basicFieldDefinitionJson)) shouldBe JsSuccess(basicFieldDefinition)
    }

    "for field definition with access" in {
      val apiFieldDefinitionsWithAccess: ApiFieldDefinitions = {
        ApiFieldDefinitions(
          ApiContext("my-context"),
          ApiVersionNbr("1.0"),
          List(
            FieldDefinition(
              name = FieldName("field-name"),
              description = "my-description",
              shortDescription = "my-shortDescription",
              hint = "my-hint",
              `type` = "STRING",
              access = AccessRequirements(devhub = DevhubAccessRequirements(NoOne, NoOne))
            )
          )
        )
      }

      Json.fromJson[ApiFieldDefinitions](Json.parse(fieldDefinitionWithAccessJson)) shouldBe JsSuccess(apiFieldDefinitionsWithAccess)
    }
  }
}
