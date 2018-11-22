resolvers += Resolver.url("hmrc-sbt-plugin-releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
resolvers += "HMRC Releases" at "https://dl.bintray.com/hmrc/releases"

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.13.0")
addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "1.2.0")
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.13")
addSbtPlugin("org.irundaia.sbt" % "sbt-sassify" % "1.4.12")
addSbtPlugin("net.ground5hark.sbt" % "sbt-concat" % "0.1.9")
addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "2.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "1.15.0")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "0.14.0")
