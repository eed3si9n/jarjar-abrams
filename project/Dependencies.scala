import sbt._

object Dependencies {
  val scala210 = "2.10.7"
  val scala211 = "2.11.12"
  val scala212 = "2.12.20"
  val scala213 = "2.13.16"
  val verify = "com.eed3si9n.verify" %% "verify" % "0.2.0"
  val parallel = "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.2"
  val junit = Seq(
    "junit" % "junit" % "4.12" % "test",
    "com.github.sbt" % "junit-interface" % "0.13.2" % "test"
  )
}
