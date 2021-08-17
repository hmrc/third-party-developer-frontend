/*
 * Copyright 2021 HM Revenue & Customs
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

import domain.models.apidefinitions._
import domain.models.apidefinitions.APIStatus.{BETA, STABLE}
import utils._
import builder._

class APISubscriptionsSpec
  extends AsyncHmrcSpec
  with LocalUserIdTracker
  with DeveloperBuilder
  with SampleSession
  with SampleApplication
  with SubscriptionTestHelperSugar {

  "groupSubscriptions" should {
    val publicAccess = Some(APIAccess(APIAccessType.PUBLIC))
    val privateAccess = Some(APIAccess(APIAccessType.PRIVATE))

    "split Private Beta APIs from public APIs " in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, BETA, access = publicAccess),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionThree, BETA, access = privateAccess),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, ApiVersion("4.0"), BETA, access = privateAccess),
            subscriptionStatus("Individual Tax", "individual-tax", taxContext, versionOne, STABLE, isTestSupport = true)
          )
        )
        .get

      groupedSubscriptions.testApis.size shouldBe 1
      groupedSubscriptions.apis.size shouldBe 1
      groupedSubscriptions.exampleApi.isDefined shouldBe false
      verifyApplicationSubscription(
        groupedSubscriptions.apis.head,
        "Individual Employment",
        "individual-employment",
        List(
          ApiVersionDefinition(versionOne, STABLE),
          ApiVersionDefinition(versionTwo, BETA, publicAccess),
          ApiVersionDefinition(versionThree, BETA, privateAccess),
          ApiVersionDefinition(ApiVersion("4.0"), BETA, privateAccess)
        )
      )
      verifyApplicationSubscription(groupedSubscriptions.testApis.head, "Individual Tax", "individual-tax", List(ApiVersionDefinition(versionOne, STABLE)))
    }

    "group subscriptions based on api service-name" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, BETA),
            subscriptionStatus("Individual Tax", "individual-tax", taxContext, versionOne, STABLE)
          )
        )
        .get

      groupedSubscriptions.apis.size shouldBe 2
      groupedSubscriptions.testApis.size shouldBe 0
      groupedSubscriptions.exampleApi.isDefined shouldBe false
      verifyApplicationSubscription(
        groupedSubscriptions.apis.head,
        "Individual Employment",
        "individual-employment",
        List(ApiVersionDefinition(versionOne, STABLE), ApiVersionDefinition(versionTwo, BETA))
      )
      verifyApplicationSubscription(groupedSubscriptions.apis(1), "Individual Tax", "individual-tax", List(ApiVersionDefinition(versionOne, STABLE)))
    }

    "take first app name if it is different" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, STABLE, subscribed = true),
            subscriptionStatus("Individual Employment Different for some reason", "individual-employment", employmentContext, versionTwo, BETA)
          )
        )
        .get

      groupedSubscriptions.apis.size shouldBe 1
      groupedSubscriptions.testApis.size shouldBe 0
      groupedSubscriptions.exampleApi.isDefined shouldBe false
      verifyApplicationSubscription(
        groupedSubscriptions.apis.head,
        "Individual Employment",
        "individual-employment",
        List(ApiVersionDefinition(versionOne, STABLE), ApiVersionDefinition(versionTwo, BETA))
      )
    }

    "return None if no subscriptions" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(List.empty)

      groupedSubscriptions shouldBe None
    }

    "identifies the example api" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Hello World", "api-example-microservice", ApiContext("api-example-microservice-context"), versionOne, STABLE, subscribed = true)
          )
        )
        .get

      groupedSubscriptions.testApis.size shouldBe 0
      groupedSubscriptions.apis.size shouldBe 0
      groupedSubscriptions.exampleApi.isDefined shouldBe true
      verifyApplicationSubscription(groupedSubscriptions.exampleApi.get, "Hello World", "api-example-microservice", List(ApiVersionDefinition(versionOne, STABLE)))
    }
  }

  "subscriptionNumberText" should {
    val apiName = "Individual Employment"
    val serviceName = "individual-employment"
    val context = ApiContext("individual-employment-context")

    "use plural in subscription number if there is no subscription" in {
      val api = apiSubscription(
        apiName,
        serviceName,
        context,
        List(
          subscriptionStatus(apiName, serviceName, context, versionOne, STABLE, subscribed = false),
          subscriptionStatus(apiName, serviceName, context, versionTwo, BETA, subscribed = false)
        )
      )

      api.subscriptionNumberText shouldBe "0 subscriptions"
    }

    "use singular in subscription number if there are 1 subscriptions" in {
      val api = apiSubscription(
        apiName,
        serviceName,
        context,
        List(
          subscriptionStatus(apiName, serviceName, context, versionOne, STABLE, subscribed = true),
          subscriptionStatus(apiName, serviceName, context, versionTwo, BETA)
        )
      )

      api.subscriptionNumberText shouldBe "1 subscription"
    }

    "use plural in subscription number if there are 2 subscriptions" in {
      val api = apiSubscription(
        apiName,
        serviceName,
        context,
        List(
          subscriptionStatus(apiName, serviceName, context, versionOne, STABLE, subscribed = true),
          subscriptionStatus(apiName, serviceName, context, versionTwo, BETA, subscribed = true)
        )
      )

      api.subscriptionNumberText shouldBe "2 subscriptions"
    }
  }

  "ajaxSubscriptionResponse" should {
    val api1Name = "Individual Employment"
    val api1Context = employmentContext
    val api1Service = "individual-employment"
    val api2Name = "Individual Tax"
    val api2Context = taxContext
    val api2Service = "individual-tax"

    "return with api context name and subscriptions count for the specific api for a PUBLIC API" in {
      val subscriptions = List(
        subscriptionStatus(api1Name, api1Service, api1Context, versionOne, STABLE, subscribed = true),
        subscriptionStatus(api1Name, api1Service, api1Context, versionTwo, BETA, subscribed = true, access = Some(APIAccess(APIAccessType.PUBLIC))),
        subscriptionStatus(api1Name, api1Service, api1Context, versionThree, STABLE, subscribed = true, access = Some(APIAccess(APIAccessType.PRIVATE))),
        subscriptionStatus(api2Name, api2Service, api2Context, versionOne, STABLE, subscribed = true),
        subscriptionStatus(api2Name, api2Service, api2Context, versionTwo, BETA, subscribed = false),
        subscriptionStatus(api2Name, api2Service, api2Context, versionThree, STABLE, subscribed = true)
      )

      val response = AjaxSubscriptionResponse.from(api1Context, versionTwo, subscriptions)

      response shouldBe AjaxSubscriptionResponse(api1Context, "API", "2 subscriptions")
    }

    "return with api context name and subscriptions count for the specific api for a PRIVATE API" in {
      val subscriptions = List(
        subscriptionStatus(api1Name, api1Service, api1Context, versionOne, STABLE, subscribed = true),
        subscriptionStatus(api1Name, api1Service, api1Context, versionTwo, BETA, subscribed = true, access = Some(APIAccess(APIAccessType.PUBLIC))),
        subscriptionStatus(api1Name, api1Service, api1Context, versionThree, STABLE, subscribed = true, access = Some(APIAccess(APIAccessType.PRIVATE))),
        subscriptionStatus(api2Name, api2Service, api2Context, versionOne, STABLE, subscribed = true),
        subscriptionStatus(api2Name, api2Service, api2Context, versionTwo, BETA, subscribed = false),
        subscriptionStatus(api2Name, api2Service, api2Context, versionThree, STABLE, subscribed = true)
      )

      val response = AjaxSubscriptionResponse.from(api1Context, versionThree, subscriptions)

      response shouldBe AjaxSubscriptionResponse(api1Context, "API", "1 subscription")
    }
  }

  "hasSubscriptions" should {
    "return false if there is no subscription" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, STABLE, subscribed = false),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, BETA, subscribed = false)
          )
        )
        .get

      groupedSubscriptions.apis.head.hasSubscriptions shouldBe false
    }

    "return true if there is one subscription" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, BETA, subscribed = false)
          )
        )
        .get

      groupedSubscriptions.apis.head.hasSubscriptions shouldBe true
    }

    "return true if there is more than one subscription" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, BETA, subscribed = true)
          )
        )
        .get

      groupedSubscriptions.apis.head.hasSubscriptions shouldBe true
    }

  }

  "isExpanded" should {
    "return false if there is no subscription" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, STABLE, subscribed = false),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, BETA, subscribed = false)
          )
        )
        .get

      groupedSubscriptions.apis.head.isExpanded shouldBe false
    }

    "return true if there is 1 subscription" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, BETA, subscribed = false)
          )
        )
        .get

      groupedSubscriptions.apis.head.isExpanded shouldBe true
    }
  }

  def apiSubscription(apiName: String, serviceName: String, context: ApiContext, subscriptions: List[APISubscriptionStatus]) =
    APISubscriptions(apiName, serviceName, context, subscriptions)
}
