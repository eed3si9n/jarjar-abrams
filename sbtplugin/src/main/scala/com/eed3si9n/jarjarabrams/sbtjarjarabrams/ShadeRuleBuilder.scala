package com.eed3si9n.jarjarabrams
package sbtjarjarabrams

/**
 * Creates shade rule but hardcoded to all targets.
 */
object ShadeRuleBuilder {
  def rename(patterns: (String, String)*): ShadeRule = ShadeRule.rename(patterns: _*).inAll
  def moveUnder(from: String, to: String): ShadeRule = ShadeRule.moveUnder(from, to).inAll
  def zap(patterns: String*): ShadeRule = ShadeRule.zap(patterns: _*).inAll
  def keep(patterns: String*): ShadeRule = ShadeRule.keep(patterns: _*).inAll
}
