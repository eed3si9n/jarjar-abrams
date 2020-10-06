package example

final case class Thing(params: String)

trait ATraitWithAVal {
  val thing = Thing("value")
}
