package io.github.tetratheta.npviewer.filter

import androidx.core.net.toUri

data class UserCosmeticRule(
  val includedDomains: List<String>, val excludedDomains: List<String>, val selector: String, val exception: Boolean
)

object UserCosmeticRules {
  fun createPayload(url: String, rules: List<UserCosmeticRule>): FilterCosmeticPayload {
    if (rules.isEmpty()) return FilterCosmeticPayload("", emptyList())
    val host = runCatching { url.toUri().host.orEmpty().lowercase() }.getOrDefault("")
    if (host.isBlank()) return FilterCosmeticPayload("", emptyList())

    val matches = rules.filter { it.matches(host) }
    if (matches.isEmpty()) return FilterCosmeticPayload("", emptyList())

    val excludedSelectors = matches.filter { it.exception }.map { it.selector }.toSet()
    val selectors = matches.asSequence().filterNot { it.exception }.map { it.selector }.filter { it !in excludedSelectors }.distinct().toList()

    if (selectors.isEmpty()) return FilterCosmeticPayload("", emptyList())

    return FilterCosmeticPayload(
      css = selectors.joinToString("\n") { "$it { display: none !important; }" }, selectors = selectors
    )
  }

  fun compile(rulesText: String): List<UserCosmeticRule> =
    rulesText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("!") && !it.startsWith("[") }.mapNotNull(::parseRule)
      .toList()

  private fun parseRule(line: String): UserCosmeticRule? {
    val separator = when {
      "#@#" in line -> "#@#"
      "##" in line -> "##"
      else -> return null
    }

    val parts = line.split(separator, limit = 2)
    if (parts.size != 2) return null

    val selector = parts[1].trim()
    if (selector.isBlank() || selector.contains("##")) return null

    val domains = parts[0].split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    val included = domains.filterNot { it.startsWith("~") }
    val excluded = domains.filter { it.startsWith("~") }.map { it.removePrefix("~") }

    return UserCosmeticRule(
      includedDomains = included, excludedDomains = excluded, selector = selector, exception = separator == "#@#"
    )
  }

  private fun UserCosmeticRule.matches(host: String): Boolean {
    val included = includedDomains.isEmpty() || includedDomains.any { host.matchesDomain(it) }
    val excluded = excludedDomains.any { host.matchesDomain(it) }
    return included && !excluded
  }

  private fun String.matchesDomain(domain: String): Boolean = this == domain || this.endsWith(".$domain")
}
