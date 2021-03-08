import Dependencies._

ThisBuild / scalaVersion := scala212
ThisBuild / organization := "com.eed3si9n.jarjarabrams"
ThisBuild / organizationName := "eed3si9n"
ThisBuild / organizationHomepage := Some(url("http://eed3si9n.com/"))
ThisBuild / version := "0.1.1-SNAPSHOT"
ThisBuild / description := "utility to shade Scala libraries"
ThisBuild / licenses := Seq(
  "Apache 2" -> new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")
)
ThisBuild / homepage := Some(url("https://github.com/eed3si9n/jarjar-abrams"))

lazy val core = project
  .enablePlugins(ContrabandPlugin)
  .settings(nocomma {
    name := "jarjar-abrams-core"

    crossScalaVersions := Vector(scala212, scala213, scala211, scala210)

    libraryDependencies += jarjar
    libraryDependencies ++= {
      if (scalaVersion.value.startsWith("2.10.")) Nil
      else Vector(verify % Test)
    }
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

    Compile / managedSourceDirectories += (Compile / generateContrabands / sourceManaged).value
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala"
    Test / sources := {
      val orig = (Test / sources).value
      if (scalaVersion.value.startsWith("2.10.")) Nil
      else orig
    }

    testFrameworks += new TestFramework("verify.runner.Framework")

    Compile / scalacOptions ++= {
      if (scalaVersion.value.startsWith("2.13.")) Vector("-Xlint")
      else Vector("-Xlint", "-Xfatal-warnings")
    }
  })

lazy val sbtplugin = project
  .enablePlugins(SbtPlugin)
  .dependsOn(core)
  .settings(nocomma {
    name := "sbt-jarjar-abrams"

    Compile / scalacOptions ++= Vector("-Xlint", "-Xfatal-warnings")

    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Vector("-Xmx1024M", "-Dplugin.version=" + version.value)
    }
    pluginCrossBuild / sbtVersion := "1.2.8"
    scriptedBufferLog := false
  })

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/eed3si9n/jarjar-abrams"),
    "scm:git@github.com:eed3si9n/jarjar-abrams.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "eed3si9n",
    name = "Eugene Yokota",
    email = "@eed3si9n",
    url = url("http://eed3si9n.com")
  )
)
ThisBuild / pomIncludeRepository := { _ =>
  false
}
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
