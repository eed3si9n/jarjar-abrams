package sbtassembly

import java.io.File

import org.pantsbuild.jarjar.ext_util.EntryStruct
import org.pantsbuild.jarjar.{JJProcessor, Keep, Zap, Rule}

import sbt._

object Shader {

  def rename(patterns: (String, String)*): ShadeRule =
    ShadeRule(rule = "rename", renames = patterns.toMap)

  def remove(patterns: String*): ShadeRule =
    ShadeRule(rule = "remove", patterns = patterns.toSet)

  def keepOnly(patterns: String*): ShadeRule =
    ShadeRule(rule = "keepOnly", patterns = patterns.toSet)

  private[sbtassembly] def shadeDirectory(rules: Seq[ShadeRule], dir: File, log: Logger): Unit = {
    val jjrules = rules flatMap { r => r.rule match {
      case "rename" =>
        r.renames.map { case (from, to) =>
          val jrule = new Rule()
          jrule.setPattern(from)
          jrule.setResult(to)
          jrule
        }
      case "remove" =>
        r.patterns.map { case pattern =>
          val jrule = new Zap()
          jrule.setPattern(pattern)
          jrule
        }
      case "keepOnly" =>
        r.patterns.map { case pattern =>
          val jrule = new Keep()
          jrule.setPattern(pattern)
          jrule
        }
      case _ => Nil
    }}

    val proc = JJProcessor(jjrules, true, true)
    val files = AssemblyUtils.getMappings(dir, Set())

    val entry = new EntryStruct
    files filter (!_._1.isDirectory) foreach { f =>
      entry.data = IO.readBytes(f._1)
      entry.name = f._2
      entry.time = -1

      proc.process(entry)

      IO.write(dir / entry.name, entry.data)
      if (f._2 != entry.name) IO.delete(f._1)
    }
  }

}

case class ShadeTarget(toCompiling: Boolean = false,
                       group: Option[String] = None,
                       artifact: Option[String] = None,
                       version: Option[String] = None) {
  private[sbtassembly] def isApplicableTo(mod: ModuleID): Boolean =
    group.isDefined && group.get == mod.organization &&
      artifact.isDefined && artifact.get == mod.name &&
      (version.isEmpty || version.get == mod.revision)
}

case class ShadeRule(rule: String,
                     renames: Map[String, String] = Map(),
                     patterns: Set[String] = Set(),
                     targets: Seq[ShadeTarget] = Seq()) {

  def applyToCompiling: ShadeRule =
    this.copy(targets = targets :+ ShadeTarget(true))

  def applyTo(group: String, artifact: String): ShadeRule =
    this.copy(targets = targets :+ ShadeTarget(group = Some(group), artifact = Some(artifact)))

  def applyTo(group: String, artifact: String, version: String): ShadeRule =
    this.copy(targets = targets :+ ShadeTarget(group = Some(group), artifact = Some(artifact), version = Some(version)))

  private[sbtassembly] def isApplicableTo(mod: ModuleID): Boolean =
    targets.exists(_.isApplicableTo(mod))

  private[sbtassembly] def isApplicableToCompiling: Boolean =
    targets.exists(_.toCompiling)

}
