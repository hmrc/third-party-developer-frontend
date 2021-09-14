package domain.models.applicationuplift

import play.api.libs.json.{Format, Json}

final case class ResponsibleIndividual(fullName: String, emailAddress: String)

object ResponsibleIndividual {
    implicit val format: Format[ResponsibleIndividual] = Json.format[ResponsibleIndividual]
}



