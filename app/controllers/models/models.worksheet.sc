import domain.models.apidefinitions.ApiVersion
import domain.models.apidefinitions.ApiContext
import controllers.models.ApiSubscriptionsFlow
import domain.models.apidefinitions.ApiIdentifier

val api1 = ApiIdentifier(ApiContext("c1"), ApiVersion("v1"))
val api2 = ApiIdentifier(ApiContext("c2"), ApiVersion("v1"))

val flow = ApiSubscriptionsFlow.allOf(Set(api1, api2))

val str = ApiSubscriptionsFlow.toSessionString(flow)

ApiSubscriptionsFlow.idFromSessionString("(c1,v1)")
val fout = ApiSubscriptionsFlow.fromSessionString(str)

fout.subscriptions