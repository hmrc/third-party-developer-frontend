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

import java.util.UUID

import connectors.ThirdPartyApplicationConnector
import domain.ApiSubscriptionFields.{SubscriptionFieldDefinition, SaveSubscriptionFieldsSuccessResponse, SaveSubscriptionFieldsAccessDeniedResponse, SubscriptionFieldValue}
import domain.{APIIdentifier, Application, Environment}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, anyString, eq => meq}
import org.mockito.Mockito.{never,verify}
import org.mockito.BDDMockito.given
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.CREATED
import service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import mocks.connector.SubscriptionFieldsConnectorMock

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import builder.SubscriptionsBuilder
import domain.AccessRequirements
import domain.DevhubAccessRequirements
import domain.DevhubAccessRequirement.NoOne
import domain.Role
import service.SubscriptionFieldsService.ValidateAgainstRole
import service.SubscriptionFieldsService.SkipRoleValidation

class SubscriptionFieldsServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar with SubscriptionsBuilder {

  val apiContext: String = "sub-ser-test"
  val apiVersion: String = "1.0"
  val applicationName: String = "third-party-application"
  val applicationId: String = "application-id"
  val clientId = "clientId"
  val application =
    Application(applicationId, clientId, applicationName, DateTime.now(), DateTime.now(), Environment.PRODUCTION)

  trait Setup extends SubscriptionFieldsConnectorMock{

    lazy val locked = false

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockConnectorsWrapper: ConnectorsWrapper = mock[ConnectorsWrapper]
    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]

    val underTest = new SubscriptionFieldsService(mockConnectorsWrapper)

    given(mockConnectorsWrapper.forEnvironment(application.deployedTo))
      .willReturn(Connectors(mockThirdPartyApplicationConnector, mockSubscriptionFieldsConnector))

    given(
      mockThirdPartyApplicationConnector
        .fetchApplicationById(meq(applicationId))(any[HeaderCarrier])
    ).willReturn(
      Future.successful(
        Some(
          Application(applicationId, clientId, "name", DateTime.now(), DateTime.now(), Environment.PRODUCTION)
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
          APIIdentifier("context", "version-1")
        )
      )

      subscriptionFieldValues shouldBe Seq.empty
    }

    "find and return matching values" in new Setup {
      private val apiIdentifier: APIIdentifier = APIIdentifier("context1", "version-1")

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
      private val apiIdentifier: APIIdentifier = APIIdentifier("context1", "version-1")

      private val subscriptionFieldValue = buildSubscriptionFieldValue("value")
      
      private val subscriptionFieldValues: Seq[SubscriptionFieldValue] = Seq(subscriptionFieldValue)
        
      val fieldDefinitions = Seq(subscriptionFieldValue.definition)

      fetchFieldValuesReturns(application.clientId, apiIdentifier.context, apiIdentifier.version)(subscriptionFieldValues)

      private val result =
        await(underTest.fetchFieldsValues(application, fieldDefinitions, apiIdentifier))

      result shouldBe subscriptionFieldValues
    }
  }

  "saveFieldValues" should {
    "save the fields" in new Setup {
      val developerRole  = ValidateAgainstRole(Role.DEVELOPER)
      
      val access = AccessRequirements.Default

      val definition = buildSubscriptionFieldValue("field-write-allowed", accessRequirements = access).definition
      
      val newValue = SubscriptionFieldValue(definition, "newValue")

      given(mockSubscriptionFieldsConnector.saveFieldValues(
          any(),
          any(),
          any(),
          any())(any[HeaderCarrier]))
        .willReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

      val result = await(underTest.saveFieldValues(developerRole, application, apiContext, apiVersion, Seq(newValue)))

      result shouldBe SaveSubscriptionFieldsSuccessResponse

      val expectedField = Map(definition.name -> newValue.value)
      verify(mockSubscriptionFieldsConnector)
        .saveFieldValues(
          meq(clientId),
          meq(apiContext),
          meq(apiVersion),
          meq(expectedField))(any[HeaderCarrier])
    }

     "save the fields fails with access denied" in new Setup {
    
      val developerRole = ValidateAgainstRole(Role.DEVELOPER)
      
      val access = AccessRequirements(devhub = DevhubAccessRequirements(NoOne, NoOne))

      val definition = buildSubscriptionFieldValue("field-denied", accessRequirements = access).definition

      val newValues = Seq(SubscriptionFieldValue(definition, "newValue"))

      val result = await(underTest.saveFieldValues(developerRole, application, apiContext, apiVersion, newValues))

      result shouldBe SaveSubscriptionFieldsAccessDeniedResponse

      verify(mockSubscriptionFieldsConnector, never())
        .saveFieldValues(any(), any(), any(), any())(any[HeaderCarrier])
    }

     "save the fields skipping role validation" in new Setup {
      val access = AccessRequirements.Default

      val definition = buildSubscriptionFieldValue("field-write-allowed", accessRequirements = access).definition
      
      val newValue = SubscriptionFieldValue(definition, "newValue")

      given(mockSubscriptionFieldsConnector.saveFieldValues(
          any(),
          any(),
          any(),
          any())(any[HeaderCarrier]))
        .willReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

      val result = await(underTest.saveFieldValues(SkipRoleValidation, application, apiContext, apiVersion, Seq(newValue)))

      result shouldBe SaveSubscriptionFieldsSuccessResponse

      val expectedField = Map(definition.name -> newValue.value)
      verify(mockSubscriptionFieldsConnector)
        .saveFieldValues(
          meq(clientId),
          meq(apiContext),
          meq(apiVersion),
          meq(expectedField))(any[HeaderCarrier])
    }
  }
}
