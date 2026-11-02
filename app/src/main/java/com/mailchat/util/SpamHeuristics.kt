package com.mailchat.util

object SpamHeuristics {

    private val urgencyPhrases = listOf(
        "dringend", "sofort", "nicht verzögern", "nicht aufschieben",
        "frist", "bis zum", "letzte chance", "letzte möglichkeit",
        "final", "endgültig", "unbedingt", "dringend erforderlich",
        "fristlos", "sofortige", "sofortige aktion", "handeln sie",
        "handeln sie jetzt", "jetzt handeln", "nicht warten",
        "droht", "bedroht", "kündigung", "kündigen", "gesperrt",
        "sperre", "account gesperrt", "konto gesperrt",
        "verifizieren", "verifizierung", "identity", "identität",
        "betrug", "betrüger", "betrügerisch", "betrugversuch",
        "phishing", "fake", "gefälscht", "nicht echt",
        "ungewöhnlich", "unerwartet", "überraschend",
        "außerordentlich", "außerordentlich",
        "fristablauf", "fristende", "letzte warnung",
        "letzte aufforderung", "unverzüglich",
        "sofortige aktion", "sofortige maßnahme",
        "dringende aktion", "dringende maßnahme",
        "letzte möglichkeit", "letzte chance",
        "keine ausnahme", "ohne ausnahme",
        "rechtliche folge", "rechtliche konsequenz",
        "strafgebühr", "strafgebühren", "mahngebühr",
        "konto gesperrt", "zugang gesperrt",
        "nicht mehr nutzbar", "nicht mehr erreichbar",
        "löschen", "endgültig löschen", "endgültig sperren",
        "urgent", "immediate action", "act now", "act immediately",
        "your account will be", "account suspended", "account disabled",
        "verify your", "confirm your", "update your",
        "unusual activity", "suspicious activity", "security alert",
        "password expired", "password will expire",
        "final notice", "last warning", "deadline",
        "click here to", "log in to verify", "confirm your identity",
        "your account has been", "we detected",
        "immediately verify", "take action now",
        "limited time", "expires soon", "time limited"
    )

    private val sensitiveInfoPhrases = listOf(
        "passwort", "kennwort", "pin", "bankverbindung", "kontonummer",
        "iban", "bic", "kreditkarte", "kreditkartennummer",
        "sozialversicherungsnummer", "sv-nummer", "steuer-id",
        "ausweis", "personalausweis", "reisepass", "führerschein",
        "geburtsdatum", "geburtstag", "telefonnummer", "handynummer",
        "adresse", "wohnadresse", "wohnort", "wohnhaft",
        "gesundheitsdaten", "krankenkasse", "versicherung",
        "benutzername", "benutzerkonto", "login-daten",
        "zugangsdaten", "zweitfaktor", "zwei-faktor",
        "2fa", "token", "geheimcode", "sicherheitscode",
        "bestätigen", "bestätigung", "bestätige deine",
        "konto bestätigen", "account bestätigen", "verifiziere",
        "konto wiederherstellen", "passwort zurücksetzen",
        "passwort ändern", "neues passwort", "neues kennwort",
        "daten eingeben", "daten angeben", "daten übermitteln",
        "daten preisgeben", "daten mitteilen",
        "password", "enter your password", "confirm your password",
        "credit card", "card number", "cvv", "security code",
        "social security", "ssn", "tax id", "taxpayer",
        "bank account", "routing number", "account number",
        "personal information", "private information",
        "enter your", "provide your", "submit your",
        "two-factor", "one-time password", "otp",
        "biometric", "fingerprint", "face id",
        "enter the code", "enter the verification",
        "your login", "your credentials", "your username"
    )

    private val genericGreetings = listOf(
        "sehr geehrte", "geehrte", "hallo", "liebe", "guten tag",
        "morgen", "mittag", "abend", "grüezi", "grüß gott",
        "servus", "grüß dich", "hallo zusammen", "liebe mitarbeiter",
        "liebe kunden", "liebe leser", "liebe nutzer",
        "dear customer", "dear user", "dear recipient",
        "sehr geehrte damen und herren", "geehrte damen und herren",
        "liebe leserinnen und leser", "liebe mitglieder",
        "liebe kolleginnen und kollegen", "liebe teammitglieder",
        "liebe leserinnen und leser", "liebe mitarbeiterinnen",
        "liebe kundeninnen", "liebe nutzerinnen"
    )

    private val suspiciousUrlPatterns = listOf(
        Regex("bit\\.ly", RegexOption.IGNORE_CASE),
        Regex("tinyurl\\.com", RegexOption.IGNORE_CASE),
        Regex("t\\.co", RegexOption.IGNORE_CASE),
        Regex("is\\.gd", RegexOption.IGNORE_CASE),
        Regex("ow\\.ly", RegexOption.IGNORE_CASE),
        Regex("short\\.url", RegexOption.IGNORE_CASE),
        Regex("goo\\.gl", RegexOption.IGNORE_CASE),
        Regex("buff\\.ly", RegexOption.IGNORE_CASE),
        Regex("adf\\.ly", RegexOption.IGNORE_CASE),
        Regex("bc\\.vc", RegexOption.IGNORE_CASE),
        Regex("v\\.gd", RegexOption.IGNORE_CASE),
        Regex("lnkd\\.in", RegexOption.IGNORE_CASE),
        Regex("rb\\.gy", RegexOption.IGNORE_CASE),
        Regex("dp\\.st", RegexOption.IGNORE_CASE),
        Regex("qr\\.nu", RegexOption.IGNORE_CASE),
        Regex("s\\.url\\.cn", RegexOption.IGNORE_CASE),
        Regex("urls\\.com", RegexOption.IGNORE_CASE),
        Regex("click\\.com", RegexOption.IGNORE_CASE)
    )

    fun extractDomain(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex < 0) return email.lowercase()
        return email.substringAfter('@').lowercase()
    }

    fun extractLinkDomains(html: String): List<String> {
        val domains = mutableSetOf<String>()
        val hrefPattern = Regex("""href\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        val srcPattern = Regex("""src\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        for (match in hrefPattern.findAll(html)) {
            domains.add(extractDomainFromUrl(match.groupValues[1]))
        }
        for (match in srcPattern.findAll(html)) {
            domains.add(extractDomainFromUrl(match.groupValues[1]))
        }
        return domains.toList()
    }

    private fun extractDomainFromUrl(url: String): String {
        val withoutScheme = url.removePrefix("https://").removePrefix("http://")
        val domain = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return domain.lowercase()
    }

    fun domainMismatch(senderDomain: String, linkDomains: List<String>): Boolean {
        if (senderDomain.isBlank() || linkDomains.isEmpty()) return false
        return linkDomains.any { linkDomain ->
            linkDomain != senderDomain && !linkDomain.endsWith(".$senderDomain") && !senderDomain.endsWith(".$linkDomain")
        }
    }

    fun lookalikeDomain(senderDomain: String, linkDomains: List<String>): Boolean {
        if (senderDomain.isBlank() || linkDomains.isEmpty()) return false
        return linkDomains.any { linkDomain ->
            linkDomain != senderDomain && isLookalike(senderDomain, linkDomain)
        }
    }

    private fun isLookalike(domain1: String, domain2: String): Boolean {
        if (domain1 == domain2) return false
        val d1 = domain1.replace("www.", "")
        val d2 = domain2.replace("www.", "")
        if (d1 == d2) return true
        val commonSuffix = commonSuffix(d1, d2)
        if (commonSuffix.isBlank()) return false
        val name1 = d1.removeSuffix(".$commonSuffix")
        val name2 = d2.removeSuffix(".$commonSuffix")
        if (name1 == name2) return true
        val lev = levenshtein(name1, name2)
        return lev <= 2 && (name1.length + name2.length) > 4
    }

    private fun commonSuffix(a: String, b: String): String {
        val minLen = minOf(a.length, b.length)
        var i = 0
        while (i < minLen && a[a.length - 1 - i] == b[b.length - 1 - i]) {
            i++
        }
        return a.substring(a.length - i)
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
        }
        return dp[m][n]
    }

    fun countMatches(text: String, patterns: List<String>): Int {
        val lower = text.lowercase()
        return patterns.count { pattern -> lower.contains(pattern.lowercase()) }
    }

    fun hasIpAddressInLinks(html: String): Boolean {
        val ipPattern = Regex("""https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")
        return ipPattern.containsMatchIn(html)
    }

    fun hasSuspiciousUrl(html: String): Boolean {
        return suspiciousUrlPatterns.any { it.containsMatchIn(html) }
    }

    fun hasGenericGreeting(text: String): Boolean {
        val lower = text.lowercase()
        return genericGreetings.any { lower.contains(it.lowercase()) }
    }

    fun extractImageDomains(html: String): List<String> {
        val domains = mutableSetOf<String>()
        val srcPattern = Regex("""src\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        for (match in srcPattern.findAll(html)) {
            domains.add(extractDomainFromUrl(match.groupValues[1]))
        }
        return domains.toList()
    }

    fun hasExternalImages(senderDomain: String, html: String): Boolean {
        if (senderDomain.isBlank()) return false
        val imageDomains = extractImageDomains(html)
        return imageDomains.any { imgDomain ->
            imgDomain != senderDomain &&
            !imgDomain.endsWith(".$senderDomain") &&
            !senderDomain.endsWith(".$imgDomain")
        }
    }

    fun returnPathMismatch(returnPath: String, fromAddress: String): Boolean {
        if (returnPath.isBlank() || fromAddress.isBlank()) return false
        val rpDomain = extractDomain(returnPath)
        val fromDomain = extractDomain(fromAddress)
        if (rpDomain.isBlank() || fromDomain.isBlank()) return false
        return rpDomain != fromDomain &&
            !rpDomain.endsWith(".$fromDomain") &&
            !fromDomain.endsWith(".$rpDomain")
    }

    fun score(body: String, senderDomain: String, linkDomains: List<String>, returnPath: String = "", fromAddress: String = ""): Int {
        var points = 0.0
        val maxPoints = 28.0

        if (domainMismatch(senderDomain, linkDomains)) points += 5.0
        if (lookalikeDomain(senderDomain, linkDomains)) points += 5.0

        val urgencyCount = countMatches(body, urgencyPhrases)
        points += when {
            urgencyCount >= 5 -> 3.0
            urgencyCount >= 3 -> 2.0
            urgencyCount >= 1 -> 1.0
            else -> 0.0
        }

        val sensitiveCount = countMatches(body, sensitiveInfoPhrases)
        points += when {
            sensitiveCount >= 5 -> 3.0
            sensitiveCount >= 3 -> 2.0
            sensitiveCount >= 1 -> 1.0
            else -> 0.0
        }

        if (hasGenericGreeting(body)) points += 1.0
        if (hasIpAddressInLinks(body)) points += 4.0
        if (hasSuspiciousUrl(body)) points += 2.0
        if (urgencyCount >= 1 && sensitiveCount >= 1) points += 2.0
        if (hasExternalImages(senderDomain, body)) points += 3.0
        if (returnPathMismatch(returnPath, fromAddress)) points += 4.0

        return ((points / maxPoints) * 100).toInt().coerceIn(0, 100)
    }

    fun isLikelyPhishing(body: String, senderDomain: String, linkDomains: List<String>, returnPath: String = "", fromAddress: String = ""): Boolean {
        return score(body, senderDomain, linkDomains, returnPath, fromAddress) >= 55
    }

    fun getIndicators(body: String, senderDomain: String, linkDomains: List<String>, returnPath: String = "", fromAddress: String = ""): List<String> {
        val indicators = mutableListOf<String>()
        if (domainMismatch(senderDomain, linkDomains)) indicators.add("domain_mismatch")
        if (lookalikeDomain(senderDomain, linkDomains)) indicators.add("lookalike_domain")
        if (countMatches(body, urgencyPhrases) >= 2) indicators.add("urgency")
        if (countMatches(body, sensitiveInfoPhrases) >= 2) indicators.add("sensitive_info_request")
        if (hasGenericGreeting(body)) indicators.add("generic_greeting")
        if (hasIpAddressInLinks(body)) indicators.add("ip_in_link")
        if (hasSuspiciousUrl(body)) indicators.add("suspicious_url")
        if (hasExternalImages(senderDomain, body)) indicators.add("external_images")
        if (returnPathMismatch(returnPath, fromAddress)) indicators.add("return_path_mismatch")
        return indicators
    }
}
