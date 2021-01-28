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

import cats.data.OptionT
import domain.models.applications.ApplicationId
import domain.models.developers.DeveloperSession
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import controllers.ApplicationRequest
import domain.models.applications._
import domain.models.apidefinitions._
import domain.models.subscriptions._
import domain.models.subscriptions.ApiSubscriptionFields._

@Singleton
class ApplicationActionService @Inject()(
  applicationService: ApplicationService,
  subscriptionFieldsService: SubscriptionFieldsService,
  openAccessApisService: OpenAccessApiService
)(implicit ec: ExecutionContext)  {

  def process[A](applicationId: ApplicationId, developerSession: DeveloperSession)(implicit request: MessagesRequest[A], hc: HeaderCarrier): OptionT[Future, ApplicationRequest[A]] = {
    import cats.implicits._

    for {
        applicationWithSubs <- OptionT(applicationService.fetchByApplicationId(applicationId))
        application = applicationWithSubs.application
        environment = application.deployedTo
        fieldDefinitions <- OptionT.liftF(subscriptionFieldsService.fetchAllFieldDefinitions(environment))
        openAccessApis <- OptionT.liftF(openAccessApisService.fetchAllOpenAccessApis(environment))
        subscriptionData <- OptionT.liftF(subscriptionFieldsService.fetchAllPossibleSubscriptions(applicationId))
        subs = toApiSubscriptionStatusSeq(applicationWithSubs, fieldDefinitions, subscriptionData)
        role <- OptionT.fromOption[Future](application.role(developerSession.developer.email))
      } yield {
        ApplicationRequest(application, environment, subs, openAccessApis, role, developerSession, request)
      }
  }

  def toApiSubscriptionStatusSeq(
    application: ApplicationWithSubscriptionData,
    subscriptionFieldDefinitions: Map[ApiContext, Map[ApiVersion, Map[FieldName, SubscriptionFieldDefinition]]],
    summaryApiDefinitions: Map[ApiContext, ApiData] ): Seq[APISubscriptionStatus] = {

    def handleContext(context: ApiContext, cdata: ApiData): Seq[APISubscriptionStatus] = {
      def handleVersion(version: ApiVersion, vdata: VersionData): APISubscriptionStatus = {
        def zipDefinitionsAndValues(): Seq[SubscriptionFieldValue] = {
          val fieldNameToDefinition = subscriptionFieldDefinitions.getOrElse(context, Map.empty).getOrElse(version, Map.empty)
          val fieldNameToValue = application.subscriptionFieldValues.getOrElse(context, Map.empty).getOrElse(version, Map.empty)

          fieldNameToDefinition.toList.map {
            case (n,d) => SubscriptionFieldValue(d, fieldNameToValue.getOrElse(n, FieldValue.empty))
          }
        }

        APISubscriptionStatus(
          name = cdata.name,
          serviceName = cdata.serviceName,
          context,
          apiVersion = ApiVersionDefinition(
            version,
            status = vdata.status,
            access = Some(vdata.access)
          ),
          subscribed = application.subscriptions.contains(ApiIdentifier(context,version)),
          requiresTrust = false, // Because these are filtered out
          fields = SubscriptionFieldsWrapper(
            applicationId = application.application.id,
            clientId = application.application.clientId,
            apiContext = context,
            apiVersion = version,
            fields = zipDefinitionsAndValues()
          ),
          isTestSupport = cdata.isTestSupport
        )
      }

      val orderDescending: Ordering[ApiVersion] = (x: ApiVersion, y: ApiVersion) => y.value.compareTo(x.value)

      cdata.versions.toSeq.sortBy(_._1)(orderDescending).map {
        case (k,v) => handleVersion(k,v)
      }.toList
    }

    summaryApiDefinitions.toList.flatMap {
      case (k,v) => handleContext(k,v)
    }
  }

}
