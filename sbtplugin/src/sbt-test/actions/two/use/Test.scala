package example

import shaded.org.typelevel.jawn._
import scala.collection.mutable.ArrayBuffer

object ApplicationMain extends App {
  val json = """{ "x": 5 }"""
  val tree = ast.JParser.parseUnsafe(json)
  println(tree)
}
