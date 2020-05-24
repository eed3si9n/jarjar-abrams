package com.eed3si9n.jarjarabrams
package sbtjarjarabrams

import sbt._

trait JarjarAbramsKeys {
  lazy val jarjarLibraryDependency = settingKey[ModuleID]("")
  lazy val jarjarShadeRules = settingKey[Seq[ShadeRule]]("")
}
object JarjarAbramsKeys extends JarjarAbramsKeys

trait JarjarAbramsInternalKeys {
  lazy val jarjarPackageBin = taskKey[File]("")
  lazy val jarjarPackageBinMappings = taskKey[Seq[(File, String)]]("")
  lazy val jarjarInputJar = taskKey[File]("")
}
object JarjarAbramsInternalKeys extends JarjarAbramsInternalKeys
