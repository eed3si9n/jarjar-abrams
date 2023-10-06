package com.eed3si9n.jarjar

import java.io.IOException

import misplaced.MisplacedClassProcessorFactory
import util.{
  EntryStruct,
  JarProcessor,
  JarProcessorChain,
  JarTransformerChain,
  RemappingClassTransformer,
  StandaloneJarProcessor
}

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Creates a new JJProcessor, which automatically generates the standard zap, keep, remap, etc processors.
 * This is a copy of the MainProcessor in JarJar with an added ScalaSigProcessor
 *
 * @param patterns               List of rules to parse.
 * @param verbose                Whether to verbosely log information.
 * @param skipManifest           If true, omits the manifest file from the processed jar.
 * @param misplacedClassStrategy The strategy to use when processing class files that are in the
 *                               wrong package (see MisplacedClassProcessorFactory.STRATEGY_* constants).
 */
class JJProcessor(
    val patterns: Seq[PatternElement],
    val verbose: Boolean,
    val skipManifest: Boolean,
    val misplacedClassStrategy: String
) extends JarProcessor {

  val zapList: Seq[Zap] = patterns.collect { case zap: Zap     => zap }
  val ruleList: Seq[Rule] = patterns.collect { case rule: Rule => rule }
  val keepList: Seq[Keep] = patterns.collect { case keep: Keep => keep }
  val renames: mutable.Map[String, String] = collection.mutable.HashMap[String, String]()

  val kp: KeepProcessor = if (keepList.isEmpty) null else new KeepProcessor(keepList.asJava)

  val pr = new PackageRemapper(ruleList.asJava, verbose)

  val processors: mutable.ArrayBuffer[JarProcessor] = collection.mutable.ArrayBuffer[JarProcessor]()
  if (skipManifest)
    processors += ManifestProcessor.getInstance
  if (kp != null)
    processors += kp

  val misplacedClassProcessor: JarProcessor =
    MisplacedClassProcessorFactory.getInstance.getProcessorForName(misplacedClassStrategy)
  processors += new ZapProcessor(zapList.asJava)
  processors += misplacedClassProcessor
  processors += new JarTransformerChain(
    Array[RemappingClassTransformer](new RemappingClassTransformer(pr))
  )

  val renamer: String => Option[String] = {
    val wildcards = PatternElement.createWildcards(ruleList.asJava).asScala

    (value: String) => {
      val result = wildcards.flatMap { wc =>
        val slashed = value.replace('.', '/') // The jarjar wildcards expect slashes instead of dots
        // Hack to replace the package object name.
        val renamed =
          Option(wc.replace(slashed)).orElse(Option(wc.replace(slashed + "/")).map(_.dropRight(1)))
        renamed.map(_.replace('/', '.')) // Unslash
      }.headOption

      result
    }
  }

  processors += new ScalaSigProcessor(renamer)
  processors += new MethodSignatureProcessor(pr)
  processors += new ResourceProcessor(pr)
  val chain = new JarProcessorChain(processors.toArray)

  @throws[IOException]
  def strip(file: Nothing): Unit = {
    if (kp != null) {
      val excludes = getExcludes
      if (excludes.nonEmpty)
        StandaloneJarProcessor.run(file, file, new ExcludeProcessor(excludes.asJava, verbose))
    }
  }

  /**
   * Returns the <code>.class</code> files to delete. As well the root-parameter as the rename ones
   * are taken in consideration, so that the concerned files are not listed in the result.
   *
   * @return the paths of the files in the jar-archive, including the <code>.class</code> suffix
   */
  def getExcludes: Set[String] =
    if (kp != null) kp.getExcludes.asScala.map { exclude =>
      val name = exclude + ".class"
      renames.getOrElse(name, name)
    }.toSet
    else Set.empty

  /**
   * @param struct entry struct to process
   * @return <code>true</code> if the entry is to include in the output jar
   */
  @throws[IOException]
  def process(struct: EntryStruct): Boolean = {
    val name = struct.name
    val keepIt = chain.process(struct)
    if (keepIt) {
      if (!name.equals(struct.name)) {
        if (kp != null) renames.put(name, struct.name)
        if (verbose) System.err.println("Renamed " + name + " -> " + struct.name)
      } else {
        if (verbose) {
          System.err.println(s"Skipped $name without processing because $name == ${struct.name}")
        }
      }
    }
    keepIt
  }
}
