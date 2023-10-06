package com.eed3si9n.jarjarabrams

import com.eed3si9n.jarjar.{ MainUtil, RulesFileParser }
import java.nio.file.Path;
import scala.collection.JavaConverters._

class Main {
  def help(): Unit = Main.printHelp()

  def process(rulesFile: Path, inJar: Path, outJar: Path): Unit = {
    if (rulesFile == null || inJar == null || outJar == null) {
      throw new IllegalArgumentException("rulesFile, inJar, and outJar are required");
    }
    val rules = RulesFileParser
      .parse(rulesFile.toFile)
      .asScala
      .toList
      .map(Shader.toShadeRule)
    val verbose = java.lang.Boolean.getBoolean("verbose")
    val skipManifest = java.lang.Boolean.getBoolean("skipManifest")
    val resetTimestamp = sys.props.get("resetTimestamp") match {
      case Some(_) => java.lang.Boolean.getBoolean("resetTimestamp")
      case None    => true
    }
    Shader.shadeFile(
      rules,
      inJar,
      outJar,
      verbose,
      skipManifest,
      resetTimestamp
    )
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    MainUtil.runMain(new Main(), args, "help")
  }

  def printHelp(): Unit = {
    val jarName = "jarjar-abrams-assembly.jar"
    val str = s"""Jar Jar Abrams - A utility to repackage and embed Java and Scala libraries

Command-line usage:

  java -jar $jarName [help]

    Prints this help message.

  java -jar $jarName process <rulesFile> <inJar> <outJar>

    Transform the <inJar> jar file, writing a new jar file to <outJar>.
    Any existing file named by <outJar> will be deleted.

    The transformation is defined by a set of rules in the file specified
    by the rules argument (see below).

Rules file format:

  The rules file is a text file, one rule per line. Leading and trailing
  whitespace is ignored. There are three types of rules:

    rule <pattern> <result>
    zap <pattern>
    keep <pattern>

  The standard rule ("rule") is used to rename classes. All references
  to the renamed classes will also be updated. If a class name is
  matched by more than one rule, only the first one will apply.

  <pattern> is a class name with optional wildcards. "**" will
  match against any valid class name substring. To match a single
  package component (by excluding "." from the match), a single "*" may
  be used instead.

  <result> is a class name which can optionally reference the
  substrings matched by the wildcards. A numbered reference is available
  for every "*" or "**" in the <pattern>, starting from left to
  right: "@1", "@2", etc. A special "@0" reference contains the entire
  matched class name.

  The "zap" rule causes any matched class to be removed from the resulting
  jar file. All zap rules are processed before renaming rules.

  The "keep" rule marks all matched classes as "roots". If any keep
  rules are defined all classes which are not reachable from the roots
  via dependency analysis are discarded when writing the output
  jar. This is the last step in the process, after renaming and zapping.
"""
    str.linesIterator.foreach(Console.err.println)
  }
}
