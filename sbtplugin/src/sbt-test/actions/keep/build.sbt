ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.11"
ThisBuild / version      := "0.1.0"

enablePlugins(JarjarAbramsPlugin)
name := "keep"
jarjarLibraryDependency := "org.typelevel" %% "jawn-ast" % "1.0.0"
jarjarShadeRules += ShadeRuleBuilder.keep("keep.**")

TaskKey[Unit]("check") := Def.taskDyn {
  val jar = (packageBin in Compile).value

  Def.task {
    IO.withTemporaryDirectory { dir ⇒
      IO.unzip(jar, dir)
      mustNotExist(dir / "removed" / "ShadeClass.class")
      mustExist(dir / "keep" / "Keeped.class")
    }
  }
}

def mustNotExist(f: File): Unit = {
  if (f.exists) sys.error("file" + f + " exists!")
}
def mustExist(f: File): Unit = {
  if (!f.exists) sys.error("file" + f + " does not exist!")
}
