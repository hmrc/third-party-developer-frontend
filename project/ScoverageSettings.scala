import scoverage.ScoverageKeys._

object ScoverageSettings {
  def apply() = Seq(
    coverageMinimumStmtTotal := 84.10,
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
      """.*\.controllers\.binders\..*""",
      """.*\.apiplatform\.modules\.applications\..*""",
      """.*\.apiplatform\.modules\.common\..*""",
      """.*\.apiplatform\.modules\.test_only\..*"""
    ).mkString(";")
  )
}
