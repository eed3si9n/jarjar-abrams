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
    val mappingBytes = mappings.filter(x => !Files.isDirectory(x._1)).map(x => Files.readAllBytes(x._1) -> x._2)
    val shader = bytcodeShader(rules, verbose)
    val newMappings = mappingBytes.flatMap(mapping => shader(mapping._1, mapping._2))
    mappings.filterNot(_._1.toFile.isDirectory).foreach(f => Files.delete(f._1))
    newMappings.foreach { case (bytes, mapping) =>
      val out = dir.resolve(mapping)
      if (!Files.exists(out.getParent)) Files.createDirectories(out.getParent)
      Files.write(out, bytes, StandardOpenOption.CREATE)
    }
  }

  def shadeInputStreams(
      rules: Seq[ShadeRule],
      mappings: Seq[(InputStream, String)],
      verbose: Boolean
  ): Seq[(InputStream, String)] = {
    val shader = inputStreamShader(rules, verbose)
    mappings.flatMap { case (inputStream, mapping) => shader(inputStream, mapping) }
  }

  def inputStreamShader(
      rules: Seq[ShadeRule],
      verbose: Boolean
  ): (InputStream, String) => Option[(InputStream, String)] =
    (inputStream, mapping) =>
      if (rules.isEmpty) Some(inputStream -> mapping)
      else {
        bytcodeShader(rules, verbose)(readAllBytes(inputStream), mapping).map { case (bytes, newMapping) =>
          new ByteArrayInputStream(bytes) { override def close(): Unit = inputStream.close() } -> newMapping
        }
      }

  def bytcodeShader(
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

      { (bytes, mapping) =>
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
        val shadedInputStream =
          if (proc.process(entry)) Some(entry.data -> entry.name)
          else None
        shadedInputStream.filterNot(a => excludes.contains(a._2))
      }
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
