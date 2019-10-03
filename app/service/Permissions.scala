package service

import domain.AccessType.{PRIVILEGED, ROPC, STANDARD}
import domain.Environment.{PRODUCTION, SANDBOX}
import domain.Role.ADMINISTRATOR
import domain.{Application, Developer}

class Permissions(user: Developer, application: Application) {

  private val isAdmin = application.role(user.email).contains(ADMINISTRATOR)

  val viewCredentialsLandingPage: Boolean = true

  val editCredentials: Boolean = (application.deployedTo, isAdmin) match {
    case (PRODUCTION, true) => true
    case (SANDBOX, true) => true
  }

  val viewSubscriptions: Boolean = application.access.accessType match {
    case STANDARD => true
    case _ => false
  }

  val editSubscriptions: Boolean =  (application.access.accessType, application.deployedTo, isAdmin) match {
    case (STANDARD, PRODUCTION, true) => true
    case (STANDARD, SANDBOX, _) => true
    case (_, _, _) => false
  }
}