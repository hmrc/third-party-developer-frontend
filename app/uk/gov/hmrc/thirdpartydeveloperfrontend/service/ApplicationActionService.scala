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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import cats.data.OptionT

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, ApiVersion}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{ApplicationRequest, UserRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions._

@Singleton
class ApplicationActionService @Inject() (
    applicationService: ApplicationService,
    subscriptionFieldsService: SubscriptionFieldsService,
    openAccessApisService: OpenAccessApiService
  )(implicit val ec: ExecutionContext
  ) {

  def process[A](applicationId: ApplicationId, userRequest: UserRequest[A])(implicit hc: HeaderCarrier): Future[Option[ApplicationRequest[A]]] = {
    import cats.implicits._

    (
      for {
        applicationWithSubs <- OptionT(applicationService.fetchByApplicationId(applicationId))
        application          = applicationWithSubs.application
        environment          = application.deployedTo
        fieldDefinitions    <- OptionT.liftF(subscriptionFieldsService.fetchAllFieldDefinitions(environment))
        openAccessApis      <- OptionT.liftF(openAccessApisService.fetchAllOpenAccessApis(environment))
        subscriptionData    <- OptionT.liftF(subscriptionFieldsService.fetchAllPossibleSubscriptions(applicationId))
        subs                 = toApiSubscriptionStatusList(applicationWithSubs, fieldDefinitions, subscriptionData)
        role                <- OptionT.fromOption[Future](application.role(userRequest.developer.email))
      } yield new ApplicationRequest(application, environment, subs, openAccessApis, role, userRequest)
    )
      .value
  }

  def toApiSubscriptionStatusList(
      application: ApplicationWithSubscriptionData,
      subscriptionFieldDefinitions: Map[ApiContext, Map[ApiVersionNbr, Map[FieldName, SubscriptionFieldDefinition]]],
      summaryApiDefinitions: List[ApiDefinition]
    ): List[APISubscriptionStatus] = {

    def handleContext(apiDefinition: ApiDefinition): List[APISubscriptionStatus] = {
      val context = apiDefinition.context

      def handleVersion(apiVersion: ApiVersion): APISubscriptionStatus = {
        val apiVersionNbr = apiVersion.versionNbr

        def zipDefinitionsAndValues(): List[SubscriptionFieldValue] = {
          val fieldNameToDefinition = subscriptionFieldDefinitions.getOrElse(context, Map.empty).getOrElse(apiVersionNbr, Map.empty)
          val fieldNameToValue      = application.subscriptionFieldValues.getOrElse(context, Map.empty).getOrElse(apiVersionNbr, Map.empty)

          fieldNameToDefinition.toList.map {
            case (n, d) => SubscriptionFieldValue(d, fieldNameToValue.getOrElse(n, FieldValue.empty))
          }
        }

        APISubscriptionStatus(
          name = apiDefinition.name,
          serviceName = apiDefinition.serviceName,
          context = context,
          apiVersion = apiVersion,
          subscribed = application.subscriptions.contains(ApiIdentifier(context, apiVersionNbr)),
          requiresTrust = false, // Because these are filtered out
          fields = SubscriptionFieldsWrapper(
            applicationId = application.application.id,
            clientId = application.application.clientId,
            apiContext = context,
            apiVersion = apiVersionNbr,
            fields = zipDefinitionsAndValues()
          ),
          isTestSupport = apiDefinition.isTestSupport
        )
      }

      apiDefinition.versionsAsList.sorted.reverse.map(handleVersion)
    }

    summaryApiDefinitions.flatMap(handleContext)
  }

}
