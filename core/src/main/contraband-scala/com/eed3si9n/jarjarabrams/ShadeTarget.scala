/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package com.eed3si9n.jarjarabrams
/**
 * This is a categorization to denote which rules are applied to what.
 * Used internally in sbt-assembly. There's nothing in Shader.shadeDirectory
 * that would enforce these target categorization.
 */
final class ShadeTarget private (
  val inAll: Boolean,
  val inProject: Boolean,
  val moduleId: Option[ModuleCoordinate]) extends Serializable {
  def isApplicableTo(mod: ModuleCoordinate): Boolean = inAll || (moduleId == Some(mod))
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ShadeTarget => (this.inAll == x.inAll) && (this.inProject == x.inProject) && (this.moduleId == x.moduleId)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "com.eed3si9n.jarjarabrams.ShadeTarget".##) + inAll.##) + inProject.##) + moduleId.##)
  }
  override def toString: String = {
    "ShadeTarget(" + inAll + ", " + inProject + ", " + moduleId + ")"
  }
  private[this] def copy(inAll: Boolean = inAll, inProject: Boolean = inProject, moduleId: Option[ModuleCoordinate] = moduleId): ShadeTarget = {
    new ShadeTarget(inAll, inProject, moduleId)
  }
  def withInAll(inAll: Boolean): ShadeTarget = {
    copy(inAll = inAll)
  }
  def withInProject(inProject: Boolean): ShadeTarget = {
    copy(inProject = inProject)
  }
  def withModuleId(moduleId: Option[ModuleCoordinate]): ShadeTarget = {
    copy(moduleId = moduleId)
  }
  def withModuleId(moduleId: ModuleCoordinate): ShadeTarget = {
    copy(moduleId = Option(moduleId))
  }
}
object ShadeTarget {
  private[jarjarabrams] def inAll: ShadeTarget = ShadeTarget(inAll = true, inProject = false, None)
  private[jarjarabrams] def inProject: ShadeTarget = ShadeTarget(inAll = false, inProject = true, None)
  private[jarjarabrams] def inModuleCoordinate(moduleId: ModuleCoordinate): ShadeTarget =
  ShadeTarget(inAll = false, inProject = false, moduleId = Some(moduleId))
  def apply(inAll: Boolean, inProject: Boolean, moduleId: Option[ModuleCoordinate]): ShadeTarget = new ShadeTarget(inAll, inProject, moduleId)
  def apply(inAll: Boolean, inProject: Boolean, moduleId: ModuleCoordinate): ShadeTarget = new ShadeTarget(inAll, inProject, Option(moduleId))
}
