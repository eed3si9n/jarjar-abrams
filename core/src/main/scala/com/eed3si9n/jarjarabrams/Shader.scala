package com.eed3si9n.jarjarabrams

import java.nio.file.{ Files, Path, StandardOpenOption }
import org.pantsbuild.jarjar.{ JJProcessor, _ }
import org.pantsbuild.jarjar.util.EntryStruct

object Shader {
  def shadeDirectory(
      rules: Seq[ShadeRule],
      dir: Path,
      mappings: Seq[(Path, String)],
      verbose: Boolean
  ): Unit = {
    val jjrules = rules flatMap { r =>
      r.shadePattern match {
        case ShadePattern.Rename(patterns) =>
          patterns.map {
            case (from, to) =>
              val jrule = new Rule()
              jrule.setPattern(from)
              jrule.setResult(to)
              jrule
          }
        case ShadePattern.Zap(patterns) =>
          patterns.map {
            case pattern =>
              val jrule = new Zap()
              jrule.setPattern(pattern)
              jrule
          }
        case ShadePattern.Keep(patterns) =>
          patterns.map {
            case pattern =>
              val jrule = new Keep()
              jrule.setPattern(pattern)
              jrule
          }
        case _ => Nil
      }
    }

    val proc = new JJProcessor(jjrules, verbose, true, null)

    /*
    jarjar MisplacedClassProcessor class transforms byte[] to a class using org.objectweb.asm.ClassReader.getClassName
    which always translates class names containing '.' into '/', regardless of OS platform.
    We need to transform any windows file paths in order for jarjar to match them properly and not omit them.
     */
    val files = mappings.map(f => if (f._2.contains('\\')) (f._1, f._2.replace('\\', '/')) else f)
    val entry = new EntryStruct
    files.filter(x => !Files.isDirectory(x._1)) foreach { f =>
      entry.data = Files.readAllBytes(f._1)
      entry.name = f._2
      entry.time = -1
      entry.skipTransform = false
      Files.delete(f._1)
      if (proc.process(entry)) {
        val out = dir.resolve(entry.name)
        if (!Files.exists(out.getParent)) {
          Files.createDirectories(out.getParent)
        }
        Files.write(out, entry.data, StandardOpenOption.CREATE)
      }
    }
    val excludes = proc.getExcludes
    excludes.foreach(exclude => Files.delete(dir.resolve(exclude)))
  }
}

sealed trait ShadePattern {
  def inAll: ShadeRule = ShadeRule(this, Vector(ShadeTarget.inAll))
  def inProject: ShadeRule = ShadeRule(this, Vector(ShadeTarget.inProject))
  def inModuleCoordinates(moduleId: ModuleCoordinate*): ShadeRule =
    ShadeRule(this, moduleId.toVector map ShadeTarget.inModuleCoordinate)
}

object ShadePattern {
  case class Rename(patterns: List[(String, String)]) extends ShadePattern
  case class Zap(patterns: List[String]) extends ShadePattern
  case class Keep(patterns: List[String]) extends ShadePattern
}
