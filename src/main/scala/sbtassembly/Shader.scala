package sbtassembly

import java.io.File

import org.pantsbuild.jarjar.ext_util.EntryStruct
import org.pantsbuild.jarjar._

import sbt._

case class ShadeRuleConfigured(rule: ShadeRule, targets: Seq[ShadeTarget] = Seq()) {

  def applyTo(moduleID: ModuleID): ShadeRuleConfigured =
    this.copy(targets = targets :+ ShadeTarget(moduleID = Some(moduleID)))

  def applyToCompiling(): ShadeRuleConfigured =
    this.copy(targets = targets :+ ShadeTarget(toCompiling = true))

  private[sbtassembly] def isApplicableTo(mod: ModuleID): Boolean =
    targets.exists(_.isApplicableTo(mod))

  private[sbtassembly] def isApplicableToCompiling: Boolean =
    targets.exists(_.toCompiling)

}

sealed trait ShadeRule

object ShadeRule {

  case class Rename(patterns: (String, String)*) extends ShadeRule

  case class Remove(patterns: String*) extends ShadeRule

  case class KeepOnly(patterns: String*) extends ShadeRule

  implicit def toShadeRuleConfigured(rule: ShadeRule): ShadeRuleConfigured = ShadeRuleConfigured(rule)

}

private[sbtassembly] case class ShadeTarget(toCompiling: Boolean = false, moduleID: Option[ModuleID] = None) {

  private[sbtassembly] def isApplicableTo(mod: ModuleID): Boolean =
    moduleID.isDefined && mod.equals(moduleID)

}

private[sbtassembly] object Shader {

  import ShadeRule._

  private[sbtassembly] def shadeDirectory(rules: Seq[ShadeRuleConfigured], dir: File, log: Logger): Unit = {
    val jjrules = rules flatMap { r => r.rule match {
      case Rename(patterns @ _*) =>
        patterns.map { case (from, to) =>
          val jrule = new Rule()
          jrule.setPattern(from)
          jrule.setResult(to)
          jrule
        }

      case Remove(patterns @ _*) =>
        patterns.map { case pattern =>
          val jrule = new Zap()
          jrule.setPattern(pattern)
          jrule
        }

      case KeepOnly(patterns @ _*) =>
        patterns.map { case pattern =>
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