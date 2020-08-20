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
  ): Unit =
    if (rules.isEmpty) {}
    else {
      val shader = bytecodeShader(rules, verbose)
      for {
        (path, name) <- mappings
        if !Files.isDirectory(path)
        bytes = Files.readAllBytes(path)
        _ = Files.delete(path)
        (shadedBytes, shadedName) <- shader(bytes, name)
        out = dir.resolve(shadedName)
        _ = Files.createDirectories(out.getParent)
        _ = Files.write(out, shadedBytes, StandardOpenOption.CREATE)
      } yield ()
    }

  def bytecodeShader(
    rules: Seq[ShadeRule],
    verbose: Boolean
  ): (Array[Byte], String) => Option[(Array[Byte], String)] =
    if (rules.isEmpty) (bytes, mapping) => Some(bytes -> mapping)
    else {
      val jjrules = rules.flatMap { r =>
        r.shadePattern match {
          case ShadePattern.Rename(patterns) =>
            patterns.map { case (from, to) =>
              val jrule = new Rule()
              jrule.setPattern(from)
              jrule.setResult(to)
              jrule
            }
          case ShadePattern.Zap(patterns) =>
            patterns.map { pattern =>
              val jrule = new Zap()
              jrule.setPattern(pattern)
              jrule
            }
          case ShadePattern.Keep(patterns) =>
            patterns.map { pattern =>
              val jrule = new Keep()
              jrule.setPattern(pattern)
              jrule
            }
          case _ => Nil
        }
      }

      val proc = new JJProcessor(jjrules, verbose, true, null)
      val excludes = proc.getExcludes

      (bytes, mapping) =>
        /*
        jarjar MisplacedClassProcessor class transforms byte[] to a class using org.objectweb.asm.ClassReader.getClassName
        which always translates class names containing '.' into '/', regardless of OS platform.
        We need to transform any windows file paths in order for jarjar to match them properly and not omit them.
         */
        val sanitizedMapping = if (mapping.contains('\\')) mapping.replace('\\', '/') else mapping
        val entry = new EntryStruct
        entry.data = bytes
        entry.name = sanitizedMapping
        entry.time = -1
        entry.skipTransform = false
        if (!excludes.contains(entry.name) && proc.process(entry))
          Some(entry.data -> entry.name)
        else
          None
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
