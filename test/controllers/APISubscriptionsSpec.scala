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

import domain.APIStatus._
import domain._
import uk.gov.hmrc.play.test.UnitSpec

class APISubscriptionsSpec extends UnitSpec with SubscriptionTestHelperSugar {

  "groupSubscriptions" should {
    val publicAccess = Some(APIAccess(APIAccessType.PUBLIC))
    val privateAccess = Some(APIAccess(APIAccessType.PRIVATE))

    "split Private Beta APIs from public APIs " in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq(
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = true),
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "2.0", BETA, access = publicAccess),
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "3.0", BETA, access = privateAccess),
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "4.0", BETA, access = privateAccess),
        subscriptionStatus("Individual Tax", "individual-tax", "individual-tax-context", "1.0", STABLE, isTestSupport = true)
      )).get

      groupedSubscriptions.testApis.size shouldBe 1
      groupedSubscriptions.apis.size shouldBe 1
      groupedSubscriptions.exampleApi.isDefined shouldBe false
      verifyApplicationSubscription(groupedSubscriptions.apis.head, "Individual Employment", "individual-employment",
        Seq(APIVersion("1.0", STABLE), APIVersion("2.0", BETA, publicAccess), APIVersion("3.0", BETA, privateAccess), APIVersion("4.0", BETA, privateAccess)))
      verifyApplicationSubscription(groupedSubscriptions.testApis.head, "Individual Tax", "individual-tax", Seq(APIVersion("1.0", STABLE)))
    }

    "group subscriptions based on api service-name" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq(
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = true),
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "2.0", BETA),
        subscriptionStatus("Individual Tax", "individual-tax", "individual-tax-context", "1.0", STABLE)
      )).get

      groupedSubscriptions.apis.size shouldBe 2
      groupedSubscriptions.testApis.size shouldBe 0
      groupedSubscriptions.exampleApi.isDefined shouldBe false
      verifyApplicationSubscription(groupedSubscriptions.apis.head, "Individual Employment", "individual-employment", Seq(APIVersion("1.0", STABLE), APIVersion("2.0", BETA)))
      verifyApplicationSubscription(groupedSubscriptions.apis(1), "Individual Tax", "individual-tax", Seq(APIVersion("1.0", STABLE)))
    }


    "take first app name if it is different" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq(
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = true),
        subscriptionStatus("Individual Employment Different for some reason", "individual-employment", "individual-employment-context", "2.0", BETA)
      )).get

      groupedSubscriptions.apis.size shouldBe 1
      groupedSubscriptions.testApis.size shouldBe 0
      groupedSubscriptions.exampleApi.isDefined shouldBe false
      verifyApplicationSubscription(groupedSubscriptions.apis.head, "Individual Employment", "individual-employment", Seq(APIVersion("1.0", STABLE), APIVersion("2.0", BETA)))
    }

    "return None if no subscriptions" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq.empty)

      groupedSubscriptions shouldBe None
    }

    "identifies the example api" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq(
        subscriptionStatus("Hello World", "api-example-microservice", "api-example-microservice-context", "1.0", STABLE, subscribed = true)
      )).get

      groupedSubscriptions.testApis.size shouldBe 0
      groupedSubscriptions.apis.size shouldBe 0
      groupedSubscriptions.exampleApi.isDefined shouldBe true
      verifyApplicationSubscription(groupedSubscriptions.exampleApi.get, "Hello World", "api-example-microservice", Seq(APIVersion("1.0", STABLE)))
    }
  }

  "subscriptionNumberText" should {
    val apiName = "Individual Employment"
    val serviceName = "individual-employment"
    val context = "individual-employment-context"

    "use plural in subscription number if there is no subscription" in {
      val api = apiSubscription(apiName, serviceName, context, Seq(
        subscriptionStatus(apiName, serviceName, context, "1.0", STABLE, subscribed = false),
        subscriptionStatus(apiName, serviceName, context, "2.0", BETA, subscribed = false)
      ))

      api.subscriptionNumberText shouldBe "0 subscriptions"
    }

    "use singular in subscription number if there are 1 subscriptions" in {
      val api = apiSubscription(apiName, serviceName, context, Seq(
        subscriptionStatus(apiName, serviceName, context, "1.0", STABLE, subscribed = true),
        subscriptionStatus(apiName, serviceName, context, "2.0", BETA)
      ))

      api.subscriptionNumberText shouldBe "1 subscription"
    }

    "use plural in subscription number if there are 2 subscriptions" in {
      val api = apiSubscription(apiName, serviceName, context, Seq(
        subscriptionStatus(apiName, serviceName, context, "1.0", STABLE, subscribed = true),
        subscriptionStatus(apiName, serviceName, context, "2.0", BETA, subscribed = true)
      ))

      api.subscriptionNumberText shouldBe "2 subscriptions"
    }
  }

  "ajaxSubscriptionResponse" should {
    val api1Name = "Individual Employment"
    val api1Context = "individual-employment-context"
    val api1Service = "individual-employment"
    val api2Name = "Individual Tax"
    val api2Context = "individual-tax-context"
    val api2Service = "individual-tax"

    "return with api context name and subscriptions count for the specific api for a PUBLIC API" in {
      val subscriptions = Seq(
        subscriptionStatus(api1Name, api1Service, api1Context, "1.0", STABLE, subscribed = true),
        subscriptionStatus(api1Name, api1Service, api1Context, "2.0", BETA, subscribed = true, access = Some(APIAccess(APIAccessType.PUBLIC))),
        subscriptionStatus(api1Name, api1Service, api1Context, "3.0", STABLE, subscribed = true, access = Some(APIAccess(APIAccessType.PRIVATE))),
        subscriptionStatus(api2Name, api2Service, api2Context, "1.0", STABLE, subscribed = true),
        subscriptionStatus(api2Name, api2Service, api2Context, "2.0", BETA, subscribed = false),
        subscriptionStatus(api2Name, api2Service, api2Context, "3.0", STABLE, subscribed = true)
      )

      val response = AjaxSubscriptionResponse.from(api1Context, "2.0", subscriptions)

      response shouldBe AjaxSubscriptionResponse(api1Context,"API", "2 subscriptions")
    }

    "return with api context name and subscriptions count for the specific api for a PRIVATE API" in {
      val subscriptions = Seq(
        subscriptionStatus(api1Name, api1Service, api1Context, "1.0", STABLE, subscribed = true),
        subscriptionStatus(api1Name, api1Service, api1Context, "2.0", BETA, subscribed = true, access = Some(APIAccess(APIAccessType.PUBLIC))),
        subscriptionStatus(api1Name, api1Service, api1Context, "3.0", STABLE, subscribed = true, access = Some(APIAccess(APIAccessType.PRIVATE))),
        subscriptionStatus(api2Name, api2Service, api2Context, "1.0", STABLE, subscribed = true),
        subscriptionStatus(api2Name, api2Service, api2Context, "2.0", BETA, subscribed = false),
        subscriptionStatus(api2Name, api2Service, api2Context, "3.0", STABLE, subscribed = true)
      )

      val response = AjaxSubscriptionResponse.from(api1Context, "3.0", subscriptions)

      response shouldBe AjaxSubscriptionResponse(api1Context, "API", "1 subscription")
    }
  }

  "hasSubscriptions" should {
    "return false if there is no subscription" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq(
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = false),
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "2.0", BETA, subscribed = false)
      )).get

      groupedSubscriptions.apis.head.hasSubscriptions shouldBe false
    }

    "return true if there is one subscription" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq(
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = true),
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "2.0", BETA, subscribed = false)
      )).get

      groupedSubscriptions.apis.head.hasSubscriptions shouldBe true
    }


    "return true if there is more than one subscription" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq(
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = true),
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "2.0", BETA, subscribed = true)
      )).get

      groupedSubscriptions.apis.head.hasSubscriptions shouldBe true
    }


  }

  "isExpanded" should {
    "return false if there is no subscription" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq(
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = false),
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "2.0", BETA, subscribed = false)
      )).get

      groupedSubscriptions.apis.head.isExpanded shouldBe false
    }

    "return true if there is 1 subscription" in {
      val groupedSubscriptions = APISubscriptions.groupSubscriptions(Seq(
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = true),
        subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "2.0", BETA, subscribed = false)
      )).get

      groupedSubscriptions.apis.head.isExpanded shouldBe true
    }
  }

  def apiSubscription(apiName: String, serviceName: String, context: String, subscriptions: Seq[APISubscriptionStatus]) = APISubscriptions(apiName, serviceName, context, subscriptions)
}
