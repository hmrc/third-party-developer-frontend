import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Pass
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Fail
import uk.gov.hmrc.apiplatform.modules.submissions.MarkedSubmissionsTestData

object MyData extends MarkedSubmissionsTestData
import MyData._

answer(Fail)(CustomersAuthorisingYourSoftware.question3).values

answer(Pass)(CustomersAuthorisingYourSoftware.question2).values

answersG(Pass)(activeQuestionnaireGroupings).values

submission.submit().declined().submit().granted().instances

submission.latestInstance.answersToQuestions



