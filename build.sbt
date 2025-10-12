import Dependencies._

ThisBuild / scalaVersion := scala212
ThisBuild / organization := "com.eed3si9n.jarjarabrams"
ThisBuild / organizationName := "eed3si9n"
ThisBuild / organizationHomepage := Some(url("http://eed3si9n.com/"))
ThisBuild / version := {
  val old = (ThisBuild / version).value
  if ((ThisBuild / isSnapshot).value) "1.9.0-SNAPSHOT"
  else old
}
ThisBuild / description := "utility to shade Scala libraries"
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / homepage := Some(url("https://github.com/eed3si9n/jarjar-abrams"))

lazy val jarjar = project
  .in(file("jarjar"))
  .disablePlugins(ScalafmtPlugin)
  .settings(nocomma {
    organization := "com.eed3si9n.jarjar"
    name := "jarjar"
    crossScalaVersions := Vector(scala212)
    crossPaths := false
    autoScalaLibrary := false
    libraryDependencies ++= Seq(
      "org.ow2.asm" % "asm" % "9.8",
      "org.ow2.asm" % "asm-commons" % "9.8",
      "org.apache.commons" % "commons-lang3" % "3.8.1",
    )
    libraryDependencies ++= junit

    mainClass := Some("com.eed3si9n.jarjar.Main")

    testFrameworks += new TestFramework("com.novocode.junit.JUnitFramework")

    assemblyMergeStrategy := {
      case PathList("module-info.class")         => MergeStrategy.discard
      case x if x.endsWith("/module-info.class") => MergeStrategy.discard
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  })

lazy val integrationTest = project
  .in(file("integration"))
  .disablePlugins(ScalafmtPlugin)
  .dependsOn(jarjar)
  .settings(nocomma {
    publish / skip := true
    crossPaths := false
    autoScalaLibrary := false
    libraryDependencies ++= junit
    testFrameworks += new TestFramework("com.novocode.junit.JUnitFramework")
    Test / fork := true
    Test / sources := {
      if (scala.util.Properties.isWin) {
        Nil
      } else {
        (Test / sources).value
      }
    }
    Test / envVars := Map(
      "JARJAR_CLASSPATH" -> (Runtime / fullClasspath).value
        .map(_.data)
        .mkString(System.getProperty("path.separator"))
    )
  })

lazy val ant_jarjar = project
  .in(file("ant-jarjar"))
  .disablePlugins(ScalafmtPlugin)
  .dependsOn(jarjar)
  .settings(nocomma {
    organization := "com.eed3si9n.jarjar"
    name := "ant-jarjar"
    crossScalaVersions := Vector(scala212)
    crossPaths := false
    autoScalaLibrary := false
    libraryDependencies ++= Seq(
      "org.apache.ant" % "ant" % "1.10.14",
    )
  })

lazy val maven_jarjar = project
  .in(file("maven-jarjar"))
  .disablePlugins(ScalafmtPlugin)
  .dependsOn(jarjar)
  .settings(nocomma {
    organization := "com.eed3si9n.jarjar"
    name := "maven-jarjar"
    crossScalaVersions := Vector(scala212)
    crossPaths := false
    autoScalaLibrary := false
    libraryDependencies ++= Seq(
      "org.apache.maven" % "maven-plugin-api" % "3.9.4",
    )
  })

lazy val jarjar_assembly = project
  .settings(nocomma {
    organization := "com.eed3si9n.jarjar"
    crossScalaVersions := Vector(scala212)
    crossPaths := false
    autoScalaLibrary := false
    name := "JarJar Assembly"
    Compile / packageBin := (jarjar / assembly).value
  })

lazy val jarjar_abrams_assembly = project
  .settings(nocomma {
    crossScalaVersions := Vector(scala212, scala213)
    name := "jarjar-abrams-assembly"
    Compile / packageBin := (core / assembly).value
  })

lazy val core = project
  .enablePlugins(ContrabandPlugin)
  .dependsOn(jarjar)
  .settings(nocomma {
    name := "jarjar-abrams-core"

    crossScalaVersions := Vector(scala212, scala213)

    libraryDependencies ++= Vector(verify % Test)
    libraryDependencies ++= {
      if (scalaVersion.value.startsWith("2.13.")) Vector(parallel)
      else Nil
    }
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

    Compile / managedSourceDirectories += (Compile / generateContrabands / sourceManaged).value
    Compile / generateContrabands / sourceManaged := baseDirectory.value / "src" / "main" / "contraband-scala"

    testFrameworks += new TestFramework("verify.runner.Framework")

    Compile / scalacOptions += "-deprecation"
    Compile / scalacOptions ++= {
      if (scalaVersion.value.startsWith("2.13.")) Vector("-Xlint", "-Xsource:3")
      else if (scalaVersion.value.startsWith("2.12.")) Vector("-Xlint", "-Xfatal-warnings")
      else Vector("-Xlint")
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
    scriptedSbt := "1.9.7"
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
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (version.value.endsWith("-SNAPSHOT")) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
ThisBuild / publishMavenStyle := true
ThisBuild / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
