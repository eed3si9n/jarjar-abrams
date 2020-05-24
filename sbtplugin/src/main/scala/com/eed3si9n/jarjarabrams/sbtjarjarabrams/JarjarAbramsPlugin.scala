package com.eed3si9n.jarjarabrams
package sbtjarjarabrams

import sbt._
import Keys._
import Defaults.{ packageTaskSettings, prefix }
import sbt.librarymanagement.ScalaModuleInfo
import Path.relativeTo
import scala.xml.{ Comment, Elem, Node => XmlNode }
import scala.xml.transform.{ RewriteRule, RuleTransformer }
import java.nio.file.Files

object JarjarAbramsPlugin extends AutoPlugin {
  object autoImport extends JarjarAbramsKeys {
    val ShadeRuleBuilder = sbtjarjarabrams.ShadeRuleBuilder
  }
  import autoImport._

  override def globalSettings: Seq[Setting[_]] = {
    jarjarShadeRules := Vector()
  }

  override def projectSettings: Seq[Setting[_]] =
    Seq(
      libraryDependencies += jarjarLibraryDependency.value,
      exportJars := true,
      // https://github.com/coursier/sbt-shading/blob/895e7f7d73a5e25bbd3aad8f3886303ccdf58a76/src/main/scala/coursier/ShadingPlugin.scala
      pomPostProcess := {
        val orig = pomPostProcess.value
        val scalaModuleInfoOpt = scalaModuleInfo.value
        val libDep = jarjarLibraryDependency.value
        val shadedModules0: Set[(String, String)] = Set(libDep).map { modId =>
          (modId.organization, crossName(modId, scalaModuleInfoOpt))
        }
        // Originally based on https://github.com/olafurpg/coursier-small/blob/408528d10cea1694c536f55ba1b023e55af3e0b2/build.sbt#L44-L56
        val transformer = new RuleTransformer(new RewriteRule {
          override def transform(node: XmlNode) = node match {
            case _: Elem if node.label == "dependency" =>
              val org = node.child.find(_.label == "groupId").fold("")(_.text.trim)
              val name = node.child.find(_.label == "artifactId").fold("")(_.text.trim)
              val isShaded = shadedModules0.contains((org, name))
              if (isShaded)
                Comment(
                  s""" shaded dependency $org:$name
                   | $node
                   |""".stripMargin
                )
              else
                node
            case _ => node
          }
        })

        node =>
          val node0 = orig(node)
          transformer.transform(node0).head
      },
      publishLocal := {
        reallyUpdateIvyXml.dependsOn(publishLocal).value
      },
      deliverLocal := {
        val scalaModuleInfoOpt = scalaModuleInfo.value
        val libDep = jarjarLibraryDependency.value
        val shadedModules0: Set[(String, String)] = Set(libDep).map { modId =>
          (modId.organization, crossName(modId, scalaModuleInfoOpt))
        }
        val file = deliverLocal.value
        updateIvyXml(file, shadedModules0)
        file
      },
    ) ++ inConfig(Compile)(baseSettings)

  import JarjarAbramsInternalKeys._
  def baseSettings: Seq[Setting[_]] =
    Seq(
      packageBin := jarjarPackageBin.value,
      jarjarPackageBin / target := crossTarget.value / (prefix(configuration.value.name) + "shaded"),
      jarjarPackageBin / logLevel := Level.Info,
      jarjarPackageBinMappings := {
        import sbt.util.CacheImplicits._
        val s = streams.value
        val input = jarjarInputJar.value
        val prev = jarjarPackageBinMappings.previous
        val dir = (jarjarPackageBin / target).value
        val rules = jarjarShadeRules.value
        val verbose = (jarjarPackageBin / logLevel).value == sbt.Level.Debug
        def doMapping: Seq[(File, String)] = {
          IO.delete(dir)
          IO.createDirectory(dir)
          IO.unzip(input, dir)
          val mappings = ((dir ** "*").get pair relativeTo(dir)) map {
            case (k, v) => k.toPath -> v
          }
          Shader.shadeDirectory(rules, dir.toPath, mappings, verbose)
          (dir ** "*").get pair relativeTo(dir)
        }
        val cachedMappings =
          Tracked
            .inputChanged[HashFileInfo, Seq[(File, String)]](s.cacheStoreFactory.make("input")) {
              (changed: Boolean, in: HashFileInfo) =>
                prev match {
                  case None => doMapping
                  case Some(last) =>
                    if (changed) doMapping
                    else last
                }
            }
        cachedMappings(FileInfo.hash(input))
      },
      jarjarInputJar := {
        val libDep = jarjarLibraryDependency.value
        val ur = update.value
        val cr = ur.configurations.find(_.configuration.name == configuration.value.name).head
        val modules = cr.modules find {
          case mr: ModuleReport =>
            val m = mr.module
            (m.organization == libDep.organization) &&
            (m.revision == libDep.revision) &&
            ((m.name == libDep.name) || (m.name == crossName(libDep, scalaModuleInfo.value)))
        }
        val mr = modules match {
          case Some(m) => m
          case _       => sys.error(s"$libDep was not found")
        }
        mr.artifacts.head._2
      },
      externalDependencyClasspath := {
        val input = jarjarInputJar.value
        val cp = externalDependencyClasspath.value
        cp filter { attr =>
          attr.data != input
        }
      },
    ) ++ packageTaskSettings(jarjarPackageBin, jarjarPackageBinMappings) ++ Seq(
      jarjarPackageBin / artifactPath := {
        val original = (jarjarPackageBin / artifactPath).value
        original.getParentFile / s"shaded-${original.getName}"
      },
      jarjarPackageBin := {
        val config = (jarjarPackageBin / packageConfiguration).value
        val s = streams.value
        Package(
          config,
          s.cacheStoreFactory,
          s.log,
          // sys.env.get("SOURCE_DATE_EPOCH").map(_.toLong * 1000).orElse(Some(0L))
        )
        config.jar
      }
    )

  def crossName(
      modId: ModuleID,
      scalaModuleInfoOpt: Option[ScalaModuleInfo]
  ): String = {
    val crossVer = modId.crossVersion
    val transformName = scalaModuleInfoOpt
      .flatMap(
        scalaInfo =>
          CrossVersion(crossVer, scalaInfo.scalaFullVersion, scalaInfo.scalaBinaryVersion)
      )
      .getOrElse(identity[String] _)
    transformName(modId.name)
  }

  def updateIvyXml(file: File, removeDeps: Set[(String, String)]): Unit = {
    import java.nio.charset.StandardCharsets
    val content = scala.xml.XML.loadFile(file)

    val updatedContent = content.copy(child = content.child.map {
      case elem: Elem if elem.label == "dependencies" =>
        elem.copy(child = elem.child.map {
          case elem: Elem if elem.label == "dependency" =>
            (elem.attributes.get("org"), elem.attributes.get("name")) match {
              case (Some(Seq(orgNode)), Some(Seq(nameNode))) =>
                val org = orgNode.text
                val name = nameNode.text
                val remove = removeDeps.contains((org, name))
                if (remove)
                  Comment(
                    s""" shaded dependency $org:$name
                       | $elem
                       |""".stripMargin
                  )
                else
                  elem
              case _ => elem
            }
          case n => n
        })
      case n => n
    })

    val printer = new scala.xml.PrettyPrinter(Int.MaxValue, 2)
    val updatedFileContent = """<?xml version="1.0" encoding="UTF-8"?>""" + '\n' +
      printer.format(updatedContent)
    Files.write(file.toPath, updatedFileContent.getBytes(StandardCharsets.UTF_8))
  }

  private val reallyUpdateIvyXml = Def.task {
    val baseFile = deliverLocal.value
    val log = streams.value.log
    val resolverName = publishLocalConfiguration.value.resolverName.getOrElse(???)
    ivyModule.value.withModule(log) {
      case (ivy, md, _) =>
        val resolver = ivy.getSettings.getResolver(resolverName)
        val artifact =
          new org.apache.ivy.core.module.descriptor.MDArtifact(md, "ivy", "ivy", "xml", true)
        log.info(s"Writing ivy.xml with shading at $baseFile")
        resolver.publish(artifact, baseFile, true)
    }
  }
}
