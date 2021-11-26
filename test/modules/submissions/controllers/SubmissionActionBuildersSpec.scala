// package modules.submissions.controllers

// import utils.AsyncHmrcSpec
// import utils.LocalUserIdTracker
// import builder.SampleSession
// import builder.SampleApplication
// import builder.DeveloperBuilder
// import controllers.SubscriptionTestHelperSugar
// import controllers.BaseController
// import play.api.test.StubControllerComponentsFactory
// import controllers.ApplicationActionBuilders
// import utils.SubmissionsTestData
// import scala.concurrent.ExecutionContext.Implicits.global
// import play.api.mvc.MessagesControllerComponents
// import modules.submissions.services.SubmissionService
// import service.ApplicationActionService
// import config.ErrorHandler
// import org.scalatestplus.play.guice.GuiceOneAppPerSuite
// import play.api.libs.crypto.CookieSigner
// import service.SessionService
// import scala.concurrent.ExecutionContext
// import config.ApplicationConfig

// class SubmissionActionBuildersSpec
//     extends AsyncHmrcSpec 
//     with SampleSession
//     with SampleApplication
//     with SubscriptionTestHelperSugar
//     with DeveloperBuilder
//     with LocalUserIdTracker
//     with SubmissionsTestData
//     with StubControllerComponentsFactory
//     with GuiceOneAppPerSuite
//     {
  
//   class TestController(
//     val submissionService: SubmissionService,
//     val applicationActionService: ApplicationActionService,
//     mcc: MessagesControllerComponents = stubMessagesControllerComponents()
//   )(implicit val ec: ExecutionContext)
//       extends BaseController(mcc)
//       with SubmissionActionBuilders
//       with ApplicationActionBuilders {
  
//     val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]
//     val sessionService: SessionService = app.injector.instanceOf[SessionService]
//     val errorHandler: ErrorHandler = app.injector.instanceOf[ErrorHandler]
//     implicit val appConfig: ApplicationConfig = app.injector.instanceOf[ApplicationConfig]
//   }

//   "submissionRefiner" should {
//     val submissionService: SubmissionService = mock[SubmissionService]
//     val applicationActionService: ApplicationActionService = mock[ApplicationActionService]
    
//     "find a submission and create the appropriate request" in {
//       val c = new TestController(submissionService, applicationActionService)

//       c.submissionRefiner(submissionId).invokeBlock(userRequest, submissionRequest =>
//         submissionRequest.submission.id shouldBe submissionRequest
//       )
//     }
//   }
// }
