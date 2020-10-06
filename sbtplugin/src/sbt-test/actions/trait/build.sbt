ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.13.1"

lazy val shadedLibrary = project
  .enablePlugins(JarjarAbramsPlugin)
  .settings(
    name := "shaded-json4s-ast",
    jarjarLibraryDependency := "com.example" %% "some-random-library" % "0.1.0-SNAPSHOT",
    jarjarShadeRules += ShadeRuleBuilder.moveUnder("example", "shaded")
  )

lazy val use = project
  .settings(
    publish := {}
  )
  .dependsOn(shadedLibrary)
