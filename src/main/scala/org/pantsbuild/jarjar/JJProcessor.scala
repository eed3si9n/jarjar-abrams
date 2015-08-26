package org.pantsbuild.jarjar

import org.pantsbuild.jarjar.ext_util.{EntryStruct, JarProcessor}

import scala.collection.JavaConverters._

class JJProcessor(val proc: JarProcessor) {

  def process(entry: EntryStruct): Boolean = proc.process(entry)

  def getExcludes(): Set[String] = {
    val field = proc.getClass().getDeclaredField("kp")
    field.setAccessible(true)
    val keepProcessor = field.get(proc)

    if (keepProcessor == null) Set()
    else {
      val method = proc.getClass().getDeclaredMethod("getExcludes")
      method.setAccessible(true)
      method.invoke(proc).asInstanceOf[java.util.Set[String]].asScala.toSet
    }
  }

}

object JJProcessor {

  def apply(patterns: Seq[PatternElement], verbose: Boolean, skipManifest: Boolean): JJProcessor =
    new JJProcessor(new MainProcessor(patterns.asJava, verbose, skipManifest))

}
