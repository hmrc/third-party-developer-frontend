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

package service

import builder.SubscriptionsBuilder
import connectors.{ApmConnector, ThirdPartyApplicationConnector}
import domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import domain.models.applications._
import domain.models.subscriptions.{AccessRequirements, DevhubAccessRequirements, FieldValue, Fields}
import domain.models.subscriptions.ApiSubscriptionFields.{SaveSubscriptionFieldsAccessDeniedResponse, SaveSubscriptionFieldsSuccessResponse, SubscriptionFieldValue}
import domain.models.subscriptions.DevhubAccessRequirement.NoOne
import mocks.connector.SubscriptionFieldsConnectorMock
import org.joda.time.DateTime
import uk.gov.hmrc.http.HeaderCarrier
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionFieldsServiceSpec extends AsyncHmrcSpec with SubscriptionsBuilder {

  val apiContext: ApiContext = ApiContext("sub-ser-test")
  val apiVersion: ApiVersion = ApiVersion("1.0")
  val versionOne: ApiVersion = ApiVersion("version-1")
  val applicationName: String = "third-party-application"
  val applicationId: ApplicationId = ApplicationId("application-id")
  val clientId = ClientId("clientId")
  val application =
    Application(applicationId, clientId, applicationName, DateTime.now(), DateTime.now(), None, Environment.PRODUCTION)

  trait Setup extends SubscriptionFieldsConnectorMock {

    lazy val locked = false

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockConnectorsWrapper: ConnectorsWrapper = mock[ConnectorsWrapper]
    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockApmConnector: ApmConnector = mock[ApmConnector]

    val underTest = new SubscriptionFieldsService(mockConnectorsWrapper, mockApmConnector)

    when(mockConnectorsWrapper.forEnvironment(application.deployedTo))
      .thenReturn(Connectors(mockThirdPartyApplicationConnector, mockSubscriptionFieldsConnector))

    when(
      mockThirdPartyApplicationConnector
        .fetchApplicationById(eqTo(applicationId))(any[HeaderCarrier])
    ).thenReturn(
      Future.successful(
        Some(
          Application(applicationId, clientId, "name", DateTime.now(), DateTime.now(), None, Environment.PRODUCTION)
        )
      )
    )
  }

  "fetchFieldsValues" should {
    "return empty sequence when there are none" in new Setup {
      private val subscriptionFieldValues = await(
        underTest.fetchFieldsValues(
          application,
          fieldDefinitions = Seq.empty,
          ApiIdentifier(ApiContext("context"), versionOne)
        )
      )

      subscriptionFieldValues shouldBe Seq.empty
    }

    "find and return matching values" in new Setup {
      private val apiIdentifier: ApiIdentifier = ApiIdentifier(ApiContext("context1"), versionOne)

      private val subscriptionFieldValue1 = buildSubscriptionFieldValue("value1")
      private val subscriptionFieldValue2 = buildSubscriptionFieldValue("value2")

      val fieldDefinitions = Seq(subscriptionFieldValue1.definition, subscriptionFieldValue2.definition)

      private val subscriptionFields: Seq[SubscriptionFieldValue] =
        Seq(subscriptionFieldValue1, subscriptionFieldValue2)

      fetchFieldValuesReturns(application.clientId, apiIdentifier.context, apiIdentifier.version)(subscriptionFields)

      private val subscriptionFieldValues =
        await(underTest.fetchFieldsValues(application, fieldDefinitions, apiIdentifier))

      subscriptionFieldValues shouldBe Seq(subscriptionFieldValue1, subscriptionFieldValue2)
    }

    "find no matching values" in new Setup {
      private val apiIdentifier: ApiIdentifier = ApiIdentifier(ApiContext("context1"), versionOne)

      private val subscriptionFieldValue = buildSubscriptionFieldValue("value")

      private val subscriptionFieldValues: Seq[SubscriptionFieldValue] = Seq(subscriptionFieldValue)

      val fieldDefinitions = Seq(subscriptionFieldValue.definition)

      fetchFieldValuesReturns(application.clientId, apiIdentifier.context, apiIdentifier.version)(subscriptionFieldValues)

      private val result =
        await(underTest.fetchFieldsValues(application, fieldDefinitions, apiIdentifier))

      result shouldBe subscriptionFieldValues
    }
  }

  "saveFieldsValues" should {
    "save the fields" in new Setup {
      val developerRole = Role.DEVELOPER

      val access = AccessRequirements.Default

      val definition1 = buildSubscriptionFieldValue("field1", accessRequirements = access).definition
      val definition2 = buildSubscriptionFieldValue("field2", accessRequirements = access).definition

      val value2 = SubscriptionFieldValue(definition2, FieldValue("oldValue2"))

      val oldValues = Seq(
        SubscriptionFieldValue(definition1, FieldValue("oldValue1")),
        SubscriptionFieldValue(definition2, FieldValue("oldValue2"))
      )

      val newValue1 = FieldValue("newValue")
      val newValuesMap: Fields.Alias = Map(definition1.name -> newValue1)

      when(mockSubscriptionFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *)(any[HeaderCarrier]))
        .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

      val result = await(underTest.saveFieldValues(developerRole, application, apiContext, apiVersion, oldValues, newValuesMap))

      result shouldBe SaveSubscriptionFieldsSuccessResponse

      val newFields1: Fields.Alias = Map(
        definition1.name -> newValue1,
        definition2.name -> value2.value
      )

      verify(mockSubscriptionFieldsConnector)
        .saveFieldValues(eqTo(clientId), eqTo(apiContext), eqTo(apiVersion), eqTo(newFields1))(any[HeaderCarrier])
    }

    "save the fields fails with write access denied" in new Setup {

      val developerRole = Role.DEVELOPER

      val access = AccessRequirements(devhub = DevhubAccessRequirements(NoOne, NoOne))

      val definition = buildSubscriptionFieldValue("field-denied", accessRequirements = access).definition

      val oldValues = Seq(SubscriptionFieldValue(definition, FieldValue("oldValue")))

      val newValues = Map(definition.name -> FieldValue("newValue"))

      val result = await(underTest.saveFieldValues(developerRole, application, apiContext, apiVersion, oldValues, newValues))

      result shouldBe SaveSubscriptionFieldsAccessDeniedResponse

      verify(mockSubscriptionFieldsConnector, never)
        .saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *)(any[HeaderCarrier])
    }
  }

  "saveBlankFieldValues" should {
    "save when old values are empty" in new Setup {
      val emptyOldValue = SubscriptionFieldValue(buildSubscriptionFieldValue("field-name").definition, FieldValue.empty)

      when(mockSubscriptionFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *)(any[HeaderCarrier]))
        .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

      val result = await(underTest.saveBlankFieldValues(application, apiContext, apiVersion, Seq(emptyOldValue)))
      result shouldBe SaveSubscriptionFieldsSuccessResponse

      val expectedSavedFields = Map(
        emptyOldValue.definition.name -> FieldValue.empty
      )

      verify(mockSubscriptionFieldsConnector)
        .saveFieldValues(eqTo(clientId), eqTo(apiContext), eqTo(apiVersion), eqTo(expectedSavedFields))(any[HeaderCarrier])
    }

    "dont save when old values are populated" in new Setup {
      val populatedValue = buildSubscriptionFieldValue("field-name")

      when(mockSubscriptionFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *)(any[HeaderCarrier]))
        .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

      val result = await(underTest.saveBlankFieldValues(application, apiContext, apiVersion, Seq(populatedValue)))
      result shouldBe SaveSubscriptionFieldsSuccessResponse

      verify(mockSubscriptionFieldsConnector, never)
        .saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *)(any[HeaderCarrier])
    }
  }
}
