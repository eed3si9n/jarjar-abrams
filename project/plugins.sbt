ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("com.eed3si9n" % "sbt-nocomma" % "0.1.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.4.6")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.1.0")
