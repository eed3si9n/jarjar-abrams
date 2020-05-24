ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.11"

lazy val shadedJawn = project
  .enablePlugins(JarjarAbramsPlugin)
  .settings(
    name := "shaded-jawn",
    jarjarLibraryDependency := "org.typelevel" %% "jawn-parser" % "1.0.0",
    jarjarShadeRules += ShadeRuleBuilder.moveUnder("org.typelevel", "shaded")
  )

lazy val use = project
  .dependsOn(shadedJawn)
