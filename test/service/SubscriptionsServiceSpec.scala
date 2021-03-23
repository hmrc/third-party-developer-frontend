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

package service

import java.util.UUID.randomUUID

import builder._
import connectors._
import domain._
import domain.models.apidefinitions._
import domain.models.applications._
import uk.gov.hmrc.time.DateTimeUtils
import utils.AsyncHmrcSpec
import scala.concurrent.Future.successful
import scala.concurrent.ExecutionContext.Implicits.global
import service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.http.HeaderCarrier
import service.SubscriptionFieldsService.SubscriptionFieldsConnector
import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldValue
import domain.models.subscriptions.FieldValue
import domain.models.subscriptions.ApiSubscriptionFields.SaveSubscriptionFieldsFailureResponse
import domain.models.subscriptions.ApiSubscriptionFields.SaveSubscriptionFieldsSuccessResponse
import utils.LocalUserIdTracker

class SubscriptionsServiceSpec extends AsyncHmrcSpec with SubscriptionsBuilder with ApplicationBuilder with LocalUserIdTracker {

  val versionOne = ApiVersion("1.0")
  val versionTwo = ApiVersion("2.0")

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector =
      mock[ThirdPartyApplicationProductionConnector]
    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector =
      mock[ThirdPartyApplicationSandboxConnector]
    val mockSubscriptionsService: SubscriptionsService = mock[SubscriptionsService]

    val mockProductionSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockSandboxSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockPushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]

    val mockAuditService: AuditService = mock[AuditService]

    val mockSubscriptionFieldsService: SubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockDeskproConnector: DeskproConnector = mock[DeskproConnector]
    val mockApmConnector: ApmConnector = mock[ApmConnector]

    val subscriptionsService = new SubscriptionsService(
      mockDeskproConnector,
      mockApmConnector,
      mockSubscriptionFieldsService,
      mockAuditService
    )

    def theProductionConnectorthenReturnTheApplication(applicationId: ApplicationId, application: Application): Unit = {
      when(mockProductionApplicationConnector.fetchApplicationById(applicationId))
        .thenReturn(successful(Some(application)))
      when(mockSandboxApplicationConnector.fetchApplicationById(applicationId)).thenReturn(successful(None))
    }

  }


  val productionApplicationId = ApplicationId("Application ID")
  val productionClientId = ClientId(s"client-id-${randomUUID().toString}")
  val productionApplication: Application =
    Application(productionApplicationId, productionClientId, "name", DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION, Some("description"), Set())

  "isSubscribedToApi" should {
    val subscriptions = Set(
      ApiIdentifier(ApiContext("first context"),versionOne),
      ApiIdentifier(ApiContext("second context"),versionOne)
    )
    val appWithData = ApplicationWithSubscriptionData(buildApplication("email@example.com"), subscriptions)

    "return false when the application has no subscriptions to the requested api version" in new Setup {
      val apiContext = ApiContext("third context")
      val apiVersion = ApiVersion("3.0")
      val subscription = ApiIdentifier(apiContext, apiVersion)

      when(mockApmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(successful(Some(appWithData)))

      private val result =
        await(subscriptionsService.isSubscribedToApi(appWithData.application.id, subscription))

      result shouldBe false
    }

    "return true when the application is subscribed to the requested api version" in new Setup {
      val apiContext = ApiContext("first context")
      val apiVersion = versionOne
      val subscription = ApiIdentifier(apiContext, apiVersion)

      when(mockApmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(successful(Some(appWithData)))
      
      private val result =
        await(subscriptionsService.isSubscribedToApi(appWithData.application.id, subscription))

      result shouldBe true
    }
  }


  "Subscribe to API" should {
    "with no subscription fields definitions" in new Setup {

      private val context = ApiContext("api1")
      private val version = versionOne

      private val subscription = ApiIdentifier(context, version)

      private val fieldDefinitions = Seq.empty

      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)

      when(mockSubscriptionFieldsService.getFieldDefinitions(eqTo(productionApplication), eqTo(subscription))(*))
        .thenReturn(successful(fieldDefinitions))

      when(mockApmConnector.subscribeToApi(eqTo(productionApplicationId), eqTo(subscription))(*))
        .thenReturn(successful(ApplicationUpdateSuccessful))

      await(subscriptionsService.subscribeToApi(productionApplication, subscription)) shouldBe ApplicationUpdateSuccessful

      verify(mockApmConnector).subscribeToApi(eqTo(productionApplicationId), eqTo(subscription))(*)
      verify(mockSubscriptionFieldsService, never).saveBlankFieldValues(*, *[ApiContext], *[ApiVersion], *)(*)
    }

    "with subscription fields definitions" should {
      "save blank subscription values" in new Setup {
        private val context = ApiContext("api1")
        private val version = versionOne

        private val subscription = ApiIdentifier(context, version)

        private val fieldDefinitions = Seq(buildSubscriptionFieldValue("name").definition)

        private val fieldDefinitionsWithoutValues = fieldDefinitions.map(d => SubscriptionFieldValue(d, FieldValue.empty))

        theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)

        when(mockSubscriptionFieldsService.getFieldDefinitions(eqTo(productionApplication), eqTo(subscription))(*))
          .thenReturn(successful(fieldDefinitions))
        when(mockSubscriptionFieldsService.fetchFieldsValues(eqTo(productionApplication), eqTo(fieldDefinitions), eqTo(subscription))(*))
          .thenReturn(successful(fieldDefinitionsWithoutValues))

        when(mockApmConnector.subscribeToApi(eqTo(productionApplicationId), eqTo(subscription))(*))
          .thenReturn(successful(ApplicationUpdateSuccessful))
        when(mockSubscriptionFieldsService.saveBlankFieldValues(*, *[ApiContext], *[ApiVersion], *)(*))
          .thenReturn(successful(SaveSubscriptionFieldsSuccessResponse))

        await(subscriptionsService.subscribeToApi(productionApplication, subscription)) shouldBe ApplicationUpdateSuccessful

        verify(mockApmConnector).subscribeToApi(eqTo(productionApplicationId), eqTo(subscription))(
          *
        )
        verify(mockSubscriptionFieldsService)
          .saveBlankFieldValues(eqTo(productionApplication), eqTo(context), eqTo(version), eqTo(fieldDefinitionsWithoutValues))(*)
      }

      "but fails to save subscription fields" in new Setup {
        private val context = ApiContext("api1")
        private val version = versionOne

        private val subscription = ApiIdentifier(context, version)

        private val fieldDefinitions = Seq(buildSubscriptionFieldValue("name").definition)

        private val fieldDefinitionsWithoutValues = fieldDefinitions.map(d => SubscriptionFieldValue(d, FieldValue.empty))

        theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)

        when(mockSubscriptionFieldsService.getFieldDefinitions(eqTo(productionApplication), eqTo(subscription))(*))
          .thenReturn(successful(fieldDefinitions))
        when(mockSubscriptionFieldsService.fetchFieldsValues(eqTo(productionApplication), eqTo(fieldDefinitions), eqTo(subscription))(*))
          .thenReturn(successful(fieldDefinitionsWithoutValues))

        when(mockApmConnector.subscribeToApi(eqTo(productionApplicationId), *)(*))
          .thenReturn(successful(ApplicationUpdateSuccessful))

        val errors = Map("fieldName" -> "failure reason")

        when(mockSubscriptionFieldsService.saveBlankFieldValues(*, *[ApiContext], *[ApiVersion], *)(*))
          .thenReturn(successful(SaveSubscriptionFieldsFailureResponse(errors)))

        private val exception = intercept[RuntimeException](
          await(subscriptionsService.subscribeToApi(productionApplication, subscription)) shouldBe ApplicationUpdateSuccessful
        )

        exception.getMessage should include("failure reason")
        exception.getMessage should include("Failed to save blank subscription field values")
      }
    }
  }
}  
