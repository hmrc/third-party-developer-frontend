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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class APISubscriptionsSpec
    extends AsyncHmrcSpec
    with LocalUserIdTracker
    with UserBuilder
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestSugar
    with SubscriptionTestHelper {

  "groupSubscriptions" should {
    val publicAccess  = ApiAccess.PUBLIC
    val privateAccess = ApiAccess.Private(false)

    "split Private Beta APIs from public APIs " in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, ApiStatus.STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, ApiStatus.BETA, access = publicAccess),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionThree, ApiStatus.BETA, access = privateAccess),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, ApiVersionNbr("4.0"), ApiStatus.BETA, access = privateAccess),
            subscriptionStatus("Individual Tax", "individual-tax", taxContext, versionOne, ApiStatus.STABLE, isTestSupport = true)
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
          ApiVersion(versionOne, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty),
          ApiVersion(versionTwo, ApiStatus.BETA, publicAccess, List.empty),
          ApiVersion(versionThree, ApiStatus.BETA, privateAccess, List.empty),
          ApiVersion(ApiVersionNbr("4.0"), ApiStatus.BETA, privateAccess, List.empty)
        )
      )
      verifyApplicationSubscription(
        groupedSubscriptions.testApis.head,
        "Individual Tax",
        "individual-tax",
        List(ApiVersion(versionOne, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty))
      )
    }

    "group subscriptions based on api service-name" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, ApiStatus.STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, ApiStatus.BETA),
            subscriptionStatus("Individual Tax", "individual-tax", taxContext, versionOne, ApiStatus.STABLE)
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
        List(ApiVersion(versionOne, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty), ApiVersion(versionTwo, ApiStatus.BETA, ApiAccess.PUBLIC, List.empty))
      )
      verifyApplicationSubscription(groupedSubscriptions.apis(1), "Individual Tax", "individual-tax", List(ApiVersion(versionOne, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty)))
    }

    "take first app name if it is different" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, ApiStatus.STABLE, subscribed = true),
            subscriptionStatus("Individual Employment Different for some reason", "individual-employment", employmentContext, versionTwo, ApiStatus.BETA)
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
        List(ApiVersion(versionOne, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty), ApiVersion(versionTwo, ApiStatus.BETA, ApiAccess.PUBLIC, List.empty))
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
            subscriptionStatus("Hello World", "api-example-microservice", ApiContext("api-example-microservice-context"), versionOne, ApiStatus.STABLE, subscribed = true)
          )
        )
        .get

      groupedSubscriptions.testApis.size shouldBe 0
      groupedSubscriptions.apis.size shouldBe 0
      groupedSubscriptions.exampleApi.isDefined shouldBe true
      verifyApplicationSubscription(
        groupedSubscriptions.exampleApi.get,
        "Hello World",
        "api-example-microservice",
        List(ApiVersion(versionOne, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty))
      )
    }
  }

  "subscriptionNumberText" should {
    val apiName     = "Individual Employment"
    val serviceName = "individual-employment"
    val context     = ApiContext("individual-employment-context")

    "use plural in subscription number if there is no subscription" in {
      val api = apiSubscription(
        apiName,
        serviceName,
        context,
        List(
          subscriptionStatus(apiName, serviceName, context, versionOne, ApiStatus.STABLE, subscribed = false),
          subscriptionStatus(apiName, serviceName, context, versionTwo, ApiStatus.BETA, subscribed = false)
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
          subscriptionStatus(apiName, serviceName, context, versionOne, ApiStatus.STABLE, subscribed = true),
          subscriptionStatus(apiName, serviceName, context, versionTwo, ApiStatus.BETA)
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
          subscriptionStatus(apiName, serviceName, context, versionOne, ApiStatus.STABLE, subscribed = true),
          subscriptionStatus(apiName, serviceName, context, versionTwo, ApiStatus.BETA, subscribed = true)
        )
      )

      api.subscriptionNumberText shouldBe "2 subscriptions"
    }
  }

  "ajaxSubscriptionResponse" should {
    val api1Name    = "Individual Employment"
    val api1Context = employmentContext
    val api1Service = "individual-employment"
    val api2Name    = "Individual Tax"
    val api2Context = taxContext
    val api2Service = "individual-tax"

    "return with api context name and subscriptions count for the specific api for a PUBLIC API" in {
      val subscriptions = List(
        subscriptionStatus(api1Name, api1Service, api1Context, versionOne, ApiStatus.STABLE, subscribed = true),
        subscriptionStatus(api1Name, api1Service, api1Context, versionTwo, ApiStatus.BETA, subscribed = true, access = ApiAccess.PUBLIC),
        subscriptionStatus(api1Name, api1Service, api1Context, versionThree, ApiStatus.STABLE, subscribed = true, access = ApiAccess.Private(false)),
        subscriptionStatus(api2Name, api2Service, api2Context, versionOne, ApiStatus.STABLE, subscribed = true),
        subscriptionStatus(api2Name, api2Service, api2Context, versionTwo, ApiStatus.BETA, subscribed = false),
        subscriptionStatus(api2Name, api2Service, api2Context, versionThree, ApiStatus.STABLE, subscribed = true)
      )

      val response = AjaxSubscriptionResponse.from(api1Context, versionTwo, subscriptions)

      response shouldBe AjaxSubscriptionResponse(api1Context, "API", "2 subscriptions")
    }

    "return with api context name and subscriptions count for the specific api for a PRIVATE API" in {
      val subscriptions = List(
        subscriptionStatus(api1Name, api1Service, api1Context, versionOne, ApiStatus.STABLE, subscribed = true),
        subscriptionStatus(api1Name, api1Service, api1Context, versionTwo, ApiStatus.BETA, subscribed = true, access = ApiAccess.PUBLIC),
        subscriptionStatus(api1Name, api1Service, api1Context, versionThree, ApiStatus.STABLE, subscribed = true, access = ApiAccess.Private(false)),
        subscriptionStatus(api2Name, api2Service, api2Context, versionOne, ApiStatus.STABLE, subscribed = true),
        subscriptionStatus(api2Name, api2Service, api2Context, versionTwo, ApiStatus.BETA, subscribed = false),
        subscriptionStatus(api2Name, api2Service, api2Context, versionThree, ApiStatus.STABLE, subscribed = true)
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
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, ApiStatus.STABLE, subscribed = false),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, ApiStatus.BETA, subscribed = false)
          )
        )
        .get

      groupedSubscriptions.apis.head.hasSubscriptions shouldBe false
    }

    "return true if there is one subscription" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, ApiStatus.STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, ApiStatus.BETA, subscribed = false)
          )
        )
        .get

      groupedSubscriptions.apis.head.hasSubscriptions shouldBe true
    }

    "return true if there is more than one subscription" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, ApiStatus.STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, ApiStatus.BETA, subscribed = true)
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
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, ApiStatus.STABLE, subscribed = false),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, ApiStatus.BETA, subscribed = false)
          )
        )
        .get

      groupedSubscriptions.apis.head.isExpanded shouldBe false
    }

    "return true if there is 1 subscription" in {
      val groupedSubscriptions = APISubscriptions
        .groupSubscriptions(
          List(
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, ApiStatus.STABLE, subscribed = true),
            subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, ApiStatus.BETA, subscribed = false)
          )
        )
        .get

      groupedSubscriptions.apis.head.isExpanded shouldBe true
    }
  }

  def apiSubscription(apiName: String, serviceName: String, context: ApiContext, subscriptions: List[APISubscriptionStatus]) =
    APISubscriptions(apiName, ServiceName(serviceName), context, subscriptions)
}
