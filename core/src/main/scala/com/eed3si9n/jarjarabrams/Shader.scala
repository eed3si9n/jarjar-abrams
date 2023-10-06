package com.eed3si9n.jarjarabrams

import java.nio.file.{ Files, Path, StandardOpenOption }
import com.eed3si9n.jarjar.{ JJProcessor, _ }
import com.eed3si9n.jarjar.util.EntryStruct
import Zip.createDirectories
import scala.collection.JavaConverters._

object Shader {
  def shadeFile(
      rules: Seq[ShadeRule],
      inputJar: Path,
      outputJar: Path,
      verbose: Boolean,
      skipManifest: Boolean,
      resetTimestamp: Boolean,
  ): Unit = {
    val shader = bytecodeShader(rules, verbose, skipManifest)
    Zip.flatMap(inputJar, outputJar, resetTimestamp) { struct0 =>
      shader(struct0.data, struct0.name).map {
        case (shadedBytes, shadedName) =>
          Zip.entryStruct(shadedName, struct0.time, shadedBytes, struct0.skipTransform)
      }
    }
  }

  def makeMappings(dir: Path): List[(Path, String)] =
    Files.walk(dir).iterator().asScala.toList.flatMap { x =>
      if (x == dir) None
      else Some(x -> dir.relativize(x).toString)
    }

  def shadeDirectory(
      rules: Seq[ShadeRule],
      dir: Path,
      mappings: Seq[(Path, String)],
      verbose: Boolean
  ): Unit = shadeDirectory(rules, dir, mappings, verbose, skipManifest = true)

  def shadeDirectory(
      rules: Seq[ShadeRule],
      dir: Path,
      mappings: Seq[(Path, String)],
      verbose: Boolean,
      skipManifest: Boolean
  ): Unit =
    if (rules.isEmpty) ()
    else {
      val shader = bytecodeShader(rules, verbose, skipManifest)
      for {
        (path, name) <- mappings
        if !Files.isDirectory(path)
        bytes = Files.readAllBytes(path)
        _ = Files.delete(path)
        (shadedBytes, shadedName) <- shader(bytes, name)
        out = dir.resolve(shadedName)
        _ = createDirectories(out.getParent)
        _ = Files.write(out, shadedBytes, StandardOpenOption.CREATE)
      } yield ()
      Files.walk(dir).iterator().asScala.toList.foreach { x =>
        if (x == dir) ()
        else Zip.resetModifiedTime(x)
      }
    }

  def bytecodeShader(
      rules: Seq[ShadeRule],
      verbose: Boolean,
      skipManifest: Boolean
  ): (Array[Byte], String) => Option[(Array[Byte], String)] =
    if (rules.isEmpty)(bytes, mapping) => Some(bytes -> mapping)
    else {
      val jjrules = rules.flatMap { r =>
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

      val proc = new JJProcessor(
        patterns = jjrules,
        verbose = verbose,
        skipManifest = skipManifest,
        misplacedClassStrategy = null
      )
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

  def toShadeRule(rule: PatternElement): ShadeRule =
    rule match {
      case r: Rule =>
        ShadeRule(ShadeRule.rename((r.getPattern, r.getResult)), Vector(ShadeTarget.inAll))
      case r: Keep => ShadeRule(ShadeRule.keep((r.getPattern)), Vector(ShadeTarget.inAll))
      case r: Zap  => ShadeRule(ShadeRule.zap((r.getPattern)), Vector(ShadeTarget.inAll))
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
