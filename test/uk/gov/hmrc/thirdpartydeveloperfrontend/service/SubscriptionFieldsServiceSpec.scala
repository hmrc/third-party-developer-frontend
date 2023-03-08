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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import java.time.{LocalDateTime, Period}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SubscriptionsBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ThirdPartyApplicationConnector}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiContext, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.{
  SaveSubscriptionFieldsAccessDeniedResponse,
  SaveSubscriptionFieldsSuccessResponse,
  SubscriptionFieldValue
}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessRequirement.NoOne
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{AccessRequirements, DevhubAccessRequirements, FieldValue, Fields}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.SubscriptionFieldsConnectorMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.Collaborator

class SubscriptionFieldsServiceSpec extends AsyncHmrcSpec with SubscriptionsBuilder {

  val apiContext: ApiContext       = ApiContext("sub-ser-test")
  val apiVersion: ApiVersion       = ApiVersion("1.0")
  val versionOne: ApiVersion       = ApiVersion("version-1")
  val applicationName: String      = "third-party-application"
  val applicationId: ApplicationId = ApplicationId.random
  val clientId                     = ClientId("clientId")

  val application =
    Application(applicationId, clientId, applicationName, LocalDateTime.now(), Some(LocalDateTime.now()), None, grantLength = Period.ofDays(547), Environment.PRODUCTION)

  trait Setup extends SubscriptionFieldsConnectorMock {

    lazy val locked = false

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockConnectorsWrapper: ConnectorsWrapper                           = mock[ConnectorsWrapper]
    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockPushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]
    val mockApmConnector: ApmConnector                                     = mock[ApmConnector]

    val underTest = new SubscriptionFieldsService(mockConnectorsWrapper, mockApmConnector)

    when(mockConnectorsWrapper.forEnvironment(application.deployedTo))
      .thenReturn(Connectors(mockThirdPartyApplicationConnector, mockSubscriptionFieldsConnector, mockPushPullNotificationsConnector))

    when(
      mockThirdPartyApplicationConnector
        .fetchApplicationById(eqTo(applicationId))(*)
    ).thenReturn(
      Future.successful(
        Some(
          Application(applicationId, clientId, "name", LocalDateTime.now(), Some(LocalDateTime.now()), None, grantLength = Period.ofDays(547), Environment.PRODUCTION)
        )
      )
    )
  }

  "saveFieldsValues" should {
    "save the fields" in new Setup {
      val developerRole = Collaborator.Roles.DEVELOPER

      val access = AccessRequirements.Default

      val definition1 = buildSubscriptionFieldValue("field1", accessRequirements = access).definition
      val definition2 = buildSubscriptionFieldValue("field2", accessRequirements = access).definition

      val value2 = SubscriptionFieldValue(definition2, FieldValue("oldValue2"))

      val oldValues = Seq(
        SubscriptionFieldValue(definition1, FieldValue("oldValue1")),
        SubscriptionFieldValue(definition2, FieldValue("oldValue2"))
      )

      val newValue1                  = FieldValue("newValue")
      val newValuesMap: Fields.Alias = Map(definition1.name -> newValue1)

      when(mockSubscriptionFieldsConnector.saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *)(*))
        .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

      val result = await(underTest.saveFieldValues(developerRole, application, apiContext, apiVersion, oldValues, newValuesMap))

      result shouldBe SaveSubscriptionFieldsSuccessResponse

      val newFields1: Fields.Alias = Map(
        definition1.name -> newValue1,
        definition2.name -> value2.value
      )

      verify(mockSubscriptionFieldsConnector)
        .saveFieldValues(eqTo(clientId), eqTo(apiContext), eqTo(apiVersion), eqTo(newFields1))(*)
    }

    "save the fields fails with write access denied" in new Setup {

      val developerRole = Collaborator.Roles.DEVELOPER

      val access = AccessRequirements(devhub = DevhubAccessRequirements(NoOne, NoOne))

      val definition = buildSubscriptionFieldValue("field-denied", accessRequirements = access).definition

      val oldValues = Seq(SubscriptionFieldValue(definition, FieldValue("oldValue")))

      val newValues = Map(definition.name -> FieldValue("newValue"))

      val result = await(underTest.saveFieldValues(developerRole, application, apiContext, apiVersion, oldValues, newValues))

      result shouldBe SaveSubscriptionFieldsAccessDeniedResponse

      verify(mockSubscriptionFieldsConnector, never)
        .saveFieldValues(*[ClientId], *[ApiContext], *[ApiVersion], *)(*)
    }
  }
}
