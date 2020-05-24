/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package com.eed3si9n.jarjarabrams
final class ShadeRule private (
  val shadePattern: com.eed3si9n.jarjarabrams.ShadePattern,
  val targets: Vector[ShadeTarget]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ShadeRule => (this.shadePattern == x.shadePattern) && (this.targets == x.targets)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "com.eed3si9n.jarjarabrams.ShadeRule".##) + shadePattern.##) + targets.##)
  }
  override def toString: String = {
    "ShadeRule(" + shadePattern + ", " + targets + ")"
  }
  private[this] def copy(shadePattern: com.eed3si9n.jarjarabrams.ShadePattern = shadePattern, targets: Vector[ShadeTarget] = targets): ShadeRule = {
    new ShadeRule(shadePattern, targets)
  }
  def withShadePattern(shadePattern: com.eed3si9n.jarjarabrams.ShadePattern): ShadeRule = {
    copy(shadePattern = shadePattern)
  }
  def withTargets(targets: Vector[ShadeTarget]): ShadeRule = {
    copy(targets = targets)
  }
}
object ShadeRule {
  
  def apply(shadePattern: com.eed3si9n.jarjarabrams.ShadePattern, targets: Vector[ShadeTarget]): ShadeRule = new ShadeRule(shadePattern, targets)
}
