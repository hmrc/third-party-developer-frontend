import scoverage.ScoverageKeys._

object ScoverageSettings {
  def apply() = Seq(
    coverageMinimumStmtTotal := 85.25,
    coverageMinimumBranchTotal := 75.0,
    coverageFailOnMinimum := true,
    coverageHighlighting := true,
    coverageExcludedPackages :=  Seq(
      "<empty>",
      "prod.*",
      "testOnlyDoNotUseInAppConf.*",
      "app.*",
      ".*Reverse.*",
      ".*Routes.*",
      "com\\.kenshoo\\.play\\.metrics\\..*",
      ".*definition.*",
      ".*BuildInfo.*",
      ".*javascript",
      "controllers.binders",
      "modules.submissions.controllers.binders",
      "uk\\.gov\\.hmrc\\.apiplatform\\.modules\\.applications\\..*",
      "uk\\.gov\\.hmrc\\.apiplatform\\.modules\\.developers\\..*",
      "uk\\.gov\\.hmrc\\.apiplatform\\.modules\\.common\\..*"
    ).mkString(";")
  )
}
