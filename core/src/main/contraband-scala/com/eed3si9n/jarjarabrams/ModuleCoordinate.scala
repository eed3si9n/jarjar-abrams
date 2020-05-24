/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package com.eed3si9n.jarjarabrams
/** stand-in for sbt's ModuleID */
final class ModuleCoordinate private (
  val organization: String,
  val name: String,
  val version: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ModuleCoordinate => (this.organization == x.organization) && (this.name == x.name) && (this.version == x.version)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "com.eed3si9n.jarjarabrams.ModuleCoordinate".##) + organization.##) + name.##) + version.##)
  }
  override def toString: String = {
    "ModuleCoordinate(" + organization + ", " + name + ", " + version + ")"
  }
  private[this] def copy(organization: String = organization, name: String = name, version: String = version): ModuleCoordinate = {
    new ModuleCoordinate(organization, name, version)
  }
  def withOrganization(organization: String): ModuleCoordinate = {
    copy(organization = organization)
  }
  def withName(name: String): ModuleCoordinate = {
    copy(name = name)
  }
  def withVersion(version: String): ModuleCoordinate = {
    copy(version = version)
  }
}
object ModuleCoordinate {
  
  def apply(organization: String, name: String, version: String): ModuleCoordinate = new ModuleCoordinate(organization, name, version)
}
