ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.18"

lazy val shadedJawn = project
  .enablePlugins(JarjarAbramsPlugin)
  .settings(
    name := "shaded-jawn",
    jarjarLibraryDependency := "org.typelevel" %% "jawn-parser" % "1.0.0",
    jarjarShadeRules += ShadeRuleBuilder.moveUnder("org.typelevel", "shaded")
  )

lazy val shadedJawnAst = project
  .enablePlugins(JarjarAbramsPlugin)
  .dependsOn(shadedJawn)
  .settings(
    name := "shaded-jawn-ast",
    jarjarLibraryDependency := "org.typelevel" %% "jawn-ast" % "1.0.0",
    jarjarShadeRules += ShadeRuleBuilder.moveUnder("org.typelevel", "shaded")
  )

lazy val use = project
  .dependsOn(shadedJawnAst)
