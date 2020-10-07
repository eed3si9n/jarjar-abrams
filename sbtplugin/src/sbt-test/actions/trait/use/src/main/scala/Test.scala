package example

import shaded.example._

class DependsOn extends shaded.example.ATraitWithAVal

object ApplicationMain extends App {
  new DependsOn
}