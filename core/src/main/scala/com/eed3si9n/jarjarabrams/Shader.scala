package com.eed3si9n.jarjarabrams

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.file.{ Files, Path, StandardOpenOption }
import org.pantsbuild.jarjar.{ JJProcessor, _ }
import org.pantsbuild.jarjar.util.EntryStruct
import com.eed3si9n.jarjarabrams.Utils.readAllBytes

object Shader {
  def shadeDirectory(
      rules: Seq[ShadeRule],
      dir: Path,
      mappings: Seq[(Path, String)],
      verbose: Boolean
  ): Unit = {
    val inputStreams = mappings.filter(x => !Files.isDirectory(x._1)).map(x => Files.newInputStream(x._1) -> x._2)
    val newMappings = shadeInputStreams(rules, inputStreams, verbose)
    mappings.filterNot(_._1.toFile.isDirectory).foreach(f => Files.delete(f._1))
    newMappings.foreach { case (inputStream, mapping) =>
      val out = dir.resolve(mapping)
      if (!Files.exists(out.getParent)) Files.createDirectories(out.getParent)
      Files.write(out, readAllBytes(inputStream), StandardOpenOption.CREATE)
    }
  }

  def shadeInputStreams(
      rules: Seq[ShadeRule],
      mappings: Seq[(InputStream, String)],
      verbose: Boolean
  ): Seq[(InputStream, String)] = {
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

    /*
    jarjar MisplacedClassProcessor class transforms byte[] to a class using org.objectweb.asm.ClassReader.getClassName
    which always translates class names containing '.' into '/', regardless of OS platform.
    We need to transform any windows file paths in order for jarjar to match them properly and not omit them.
     */
    val sanitizedMappings =
      mappings.map(f => if (f._2.contains('\\')) (f._1, f._2.replace('\\', '/')) else f)
    val shadedInputStreams = sanitizedMappings.flatMap { f =>
      val entry = new EntryStruct
      entry.data = readAllBytes(f._1)
      entry.name = f._2
      entry.time = -1
      entry.skipTransform = false
      if (proc.process(entry)) Some(new ByteArrayInputStream(entry.data) -> entry.name)
      else None
    }
    val excludes = proc.getExcludes
    shadedInputStreams.filterNot(mapping => excludes.contains(mapping._2))
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
