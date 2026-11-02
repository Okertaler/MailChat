package com.mailchat

import org.junit.Test
import javax.mail.Session
import javax.mail.internet.MimeMessage
import java.io.ByteArrayInputStream
import java.util.Properties

class CidImageParsingTest {

    private val rawEmail = """
Return-Path: <test@example.org>
From: Deutsche Post AG <ankuendigung@brief.deutschepost.de>
To: florescu@web.de
Subject: Ein Brief ist unterwegs zu Ihnen
MIME-Version: 1.0
Content-Type: multipart/mixed; 
	boundary="----=_Part_1416606_1079011726.1791512992625"

------=_Part_1416606_1079011726.1791512992625
Content-Type: multipart/related; 
	boundary="----=_Part_1416607_763117999.1791512992625"

------=_Part_1416607_763117999.1791512992625
Content-Type: multipart/alternative; 
	boundary="----=_Part_1416608_1500194290.1791512992628"

------=_Part_1416608_1500194290.1791512992628
Content-Type: text/plain; charset=UTF-8
Content-Transfer-Encoding: quoted-printable

Lieber Herr Marc Florescu,=20
ein Brief ist unterwegs.

------=_Part_1416608_1500194290.1791512992628
Content-Type: text/html;charset=UTF-8
Content-Transfer-Encoding: quoted-printable

<html><body><p>Lieber Herr Marc Florescu,</p>=
<p><img src=3D"cid:Umschlag_20261009_04-29.png" alt=3D"Vorschau" class=3D"avis-image" /></p>=
</body></html>

------=_Part_1416608_1500194290.1791512992628--

------=_Part_1416607_763117999.1791512992625
Content-Type: image/png
Content-Transfer-Encoding: base64
Content-Disposition: inline
Content-ID: <Umschlag_20261009_04-29.png>

iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAFUlEQVR42mP8z8BQz0AEYBxVSF+F
APhKBAIAHKMHAgEBnBcCAQEr1wMAAmdFAgEBK9cDAAGMFwIBASvXAwABK9cDAAEr1wMAASvXAwAB
K9cDAAFQDwMAAVAPAwABUA8DAAGUFwIBASs3BAABJA8EAgJWbggAAkgeCARUbggEAkgeCARYbggE
AlxuCASCdAIAAwQCAQEr1wMAASvXAwABUA8DAAGUFwIBASs3BAABJA8EAgJWbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEr1wMAASvXAwABUA8DAAGUFwIBASs3BAABJA8EAgJWbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEAK9cDAAFQDwMAAVAPAwABlBcCAQErNwQAASQPBAINbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEr1wMAASvXAwABUA8DAAGUFwIBASs3BAABJA8EAgJWbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEr1wMAASvXAwABUA8DAAGUFwIBASs3BAABJA8EAgJWbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEAK9cDAAFQDwMAAVAPAwABlBcCAQErNwQAASQPBAINbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEr1wMAASvXAwABUA8DAAGUFwIBASs3BAABJA8EAgJWbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEr1wMAASvXAwABUA8DAAGUFwIBASs3BAABJA8EAgJWbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEAK9cDAAFQDwMAAVAPAwABlBcCAQErNwQAASQPBAINbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEr1wMAASvXAwABUA8DAAGUFwIBASs3BAABJA8EAgJWbggEAkgeCARUbggE
AlxuCASCdAIAAwQCAQEr1wMAASvXAwABlBcCAQErNwQAASQPBAINbggEAkgeCARUbggEAlxuCASC
dAIAAwQCAQEAK9cDAAFQDwMAAVAPAw==

------=_Part_1416607_763117999.1791512992625--

------=_Part_1416606_1079011726.1791512992625--
    """.trimIndent()

    private fun bodyPartsOf(part: javax.mail.Part): List<javax.mail.BodyPart> {
        return try {
            if (part.isMimeType("multipart/*")) {
                val multipart = part.content as? javax.mail.Multipart ?: return emptyList()
                (0 until multipart.count).map { multipart.getBodyPart(it) }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractBodyRecursive(part: javax.mail.Part): String {
        return try {
            if (part.isMimeType("text/plain")) {
                part.content.toString()
            } else if (part.isMimeType("text/html")) {
                part.content.toString()
            } else if (part.isMimeType("multipart/*")) {
                var htmlBody: String? = null
                var plainBody: String? = null
                for (subPart in bodyPartsOf(part)) {
                    if (htmlBody != null && plainBody != null) break
                    if (subPart.isMimeType("text/plain") && plainBody == null) {
                        plainBody = subPart.content.toString()
                    } else if (subPart.isMimeType("text/html") && htmlBody == null) {
                        htmlBody = subPart.content.toString()
                    } else if (subPart.isMimeType("multipart/*")) {
                        val nested = extractBodyRecursive(subPart)
                        if (nested.isNotBlank()) {
                            if (subPart.contentType?.contains("html") == true && htmlBody == null) {
                                htmlBody = nested
                            } else if (plainBody == null) {
                                plainBody = nested
                            } else if (htmlBody == null) {
                                htmlBody = nested
                            }
                        }
                    }
                }
                htmlBody ?: plainBody ?: ""
            } else {
                part.content.toString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    @Test
    fun `parse email and verify cid image extraction plus replacement`() {
        val session = Session.getInstance(Properties())
        val mail = MimeMessage(session, ByteArrayInputStream(rawEmail.toByteArray()))

        // 1. body extraction
        val bodyFull = extractBodyRecursive(mail)
        println("=== BODY (html=$bodyFull.contains("<")) ===")
        println(bodyFull.take(300))
        assert(bodyFull.contains("<")) { "bodyFull should be HTML" }
        assert(bodyFull.contains("cid:", ignoreCase = true)) { "HTML should contain cid reference" }

        // 2. attachment collection (mirrors ImapService.collectAttachments/saveAttachment)
        data class Att(val mimeType: String, val contentId: String, val fileName: String?)
        val attachments = mutableListOf<Att>()
        fun collect(part: javax.mail.Part) {
            if (part.isMimeType("multipart/*")) {
                bodyPartsOf(part).forEach { collect(it) }
            } else {
                val mimeType = part.contentType?.substringBefore(";")?.trim() ?: "application/octet-stream"
                val contentId = part.getHeader("Content-ID")?.firstOrNull()?.trim()?.removeSurrounding("<", ">") ?: ""
                val fileName = part.fileName
                    ?: if (mimeType.startsWith("image/") && contentId.isNotBlank()) contentId
                    else return
                attachments.add(Att(mimeType, contentId, fileName))
            }
        }
        collect(mail)
        println("=== ATTACHMENTS (${attachments.size}) ===")
        attachments.forEach { println(it) }

        val cidImages = attachments.filter { it.contentId.isNotBlank() && it.mimeType.startsWith("image/") }
        assert(cidImages.isNotEmpty()) { "FAIL: no CID image found! contentId extraction broken" }

        // 3. replacement (mirrors FullEmailView htmlForView, new logic)
        val cidRefs = Regex("cid:([^\"'>\\s]+)", RegexOption.IGNORE_CASE).findAll(bodyFull)
            .map { it.groupValues[1].removeSurrounding("<", ">") }
            .distinct()
            .toList()
        assert(cidRefs == listOf("Umschlag_20261009_04-29.png")) { "FAIL: cidRefs extraction wrong: $cidRefs" }

        // case A: attachment with contentId (new fetches)
        var h = bodyFull
        for (img in attachments.filter { it.mimeType.startsWith("image/") }) {
            val refs = listOfNotNull(img.contentId.takeIf { it.isNotBlank() }, img.fileName?.takeIf { it.isNotBlank() })
            if (refs.isEmpty()) continue
            val dataUri = "data:${img.mimeType};base64,AAAA"
            for (ref in refs) {
                val cid = Regex.escape(ref)
                h = h.replace(Regex("cid:<$cid>", RegexOption.IGNORE_CASE), dataUri)
                h = h.replace(Regex("cid:$cid", RegexOption.IGNORE_CASE), dataUri)
            }
        }
        println("=== AFTER REPLACE (contentId): still cid=${h.contains("cid:")}")
        assert(!h.contains("cid:")) { "FAIL: cid reference not replaced! contentId='${cidImages.first().contentId}'" }
        assert(h.contains("data:image/png;base64")) { "FAIL: data uri not injected" }

        // case B: legacy attachment without contentId, matched by fileName only
        val legacy = Att("image/png", "", "Umschlag_20261009_04-29.png")
        var h2 = bodyFull
        run {
            val img = legacy
            val refs = listOfNotNull(img.contentId.takeIf { it.isNotBlank() }, img.fileName?.takeIf { it.isNotBlank() })
            assert(refs.isNotEmpty())
            val dataUri = "data:${img.mimeType};base64,AAAA"
            for (ref in refs) {
                val cid = Regex.escape(ref)
                h2 = h2.replace(Regex("cid:<$cid>", RegexOption.IGNORE_CASE), dataUri)
                h2 = h2.replace(Regex("cid:$cid", RegexOption.IGNORE_CASE), dataUri)
            }
        }
        println("=== AFTER REPLACE (fileName fallback): still cid=${h2.contains("cid:")}")
        assert(!h2.contains("cid:")) { "FAIL: fileName fallback did not replace cid reference" }
    }
}
