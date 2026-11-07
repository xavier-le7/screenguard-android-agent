package fr.sevenskies.screenguard.network

/**
 * Domain-only matching from the server's blocked_domains (no URL path,
 * content, cookies, user identity or query history is retained).
 */
object DomainFilter {
    fun normalise(value: String): String? {
        val name = value.lowercase().trim().trimEnd('.')
        if (name.length !in 1..253 ||
            name.split('.').any { label ->
                label.length !in 1..63 ||
                    !label.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))
            }) return null
        return name
    }

    fun blocked(query: String, blockedDomains: Set<String>): Boolean {
        val host = normalise(query) ?: return false
        return blockedDomains.any { raw ->
            val domain = normalise(raw) ?: return@any false
            host == domain || host.endsWith(".$domain")
        }
    }
}
