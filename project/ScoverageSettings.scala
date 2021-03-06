import scoverage.ScoverageKeys._

object ScoverageSettings {
  def apply() = Seq(
    coverageMinimum := 85,
    coverageFailOnMinimum := true,
    coverageHighlighting := true,
    coverageExcludedPackages :=  Seq(
      "<empty>",
      "prod.*;" +
      "testOnlyDoNotUseInAppConf.*;" +
      "app.*;" +
      ".*Reverse.*",
      ".*Routes.*",
      "com\\.kenshoo\\.play\\.metrics\\.*",
      ".*definition.*",
      ".*BuildInfo.*",
      ".*javascript"
    ).mkString(";")
  )
}
