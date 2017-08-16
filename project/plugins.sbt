logLevel := Level.Warn

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.2")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0-M1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.1")

// Needed for scoverage snapshot
resolvers += Opts.resolver.sonatypeSnapshots
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1-SNAPSHOT")
//addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.8")
