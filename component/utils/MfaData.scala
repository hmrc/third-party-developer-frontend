package utils

trait MfaData extends ComponentTestDeveloperBuilder {
  val accessCode = "123456"
  val mobileNumber = "+447890123456"
  val authAppMfaId = authenticatorAppMfaDetails.id
  val smsMfaId = smsMfaDetails.id
  val deviceCookieName = "DEVICE_SESS_ID"
  val deviceCookieValue = "a6b5b0cca96fef5ffc66edffd514a9239b46b4e869fc10f6-9193-42b4-97f2-87886c972ad4"
}
