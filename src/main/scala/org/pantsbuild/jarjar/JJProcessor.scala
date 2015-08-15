package org.pantsbuild.jarjar

import org.pantsbuild.jarjar.ext_util.{EntryStruct, JarProcessor}

import scala.collection.JavaConverters._

class JJProcessor(val proc: JarProcessor) {

  def process(entry: EntryStruct): Unit = proc.process(entry)

}

object JJProcessor {

  def apply(patterns: Seq[PatternElement], verbose: Boolean, skipManifest: Boolean): JJProcessor =
    new JJProcessor(new MainProcessor(patterns.asJava, verbose, skipManifest))

}
