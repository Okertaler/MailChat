package com.mailchat

import com.mailchat.util.AssistantQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantQueryTest {

    @Test
    fun `spam intent detected in german question`() {
        val intents = AssistantQuery.detectIntents("Welcher Absender wurde als Spam markiert?")
        assertTrue(intents.contains("spam"))
    }

    @Test
    fun `no intent for normal content question`() {
        val intents = AssistantQuery.detectIntents("Was hat mir Max wegen Steuer geschrieben?")
        assertTrue(intents.isEmpty())
    }

    @Test
    fun `extract keywords removes stopwords and keeps content words`() {
        val kws = AssistantQuery.extractKeywords("Was hat mir Max letzte Woche wegen Steuer geschrieben?")
        assertTrue("max" in kws)
        assertTrue("steuer" in kws)
        assertTrue("geschrieben" in kws || "wegen" in kws)
        assertTrue(kws.size <= 5)
    }

    @Test
    fun `time window last week is about 7 days back`() {
        val now = System.currentTimeMillis()
        val since = AssistantQuery.sinceTimestamp("Was geschah letzte Woche?", now)
        val days = (now - since) / (24L * 60 * 60 * 1000)
        assertTrue("days=$days", days in 6..8)
    }

    @Test
    fun `time window with explicit number`() {
        val now = System.currentTimeMillis()
        val since = AssistantQuery.sinceTimestamp("in den letzten 14 Tagen", now)
        val days = (now - since) / (24L * 60 * 60 * 1000)
        assertTrue("days=$days", days in 13..15)
    }

    @Test
    fun `default window is about 90 days`() {
        val now = System.currentTimeMillis()
        val since = AssistantQuery.sinceTimestamp("Zeig mir Rechnungen", now)
        val days = (now - since) / (24L * 60 * 60 * 1000)
        assertTrue("days=$days", days in 89..91)
    }

    @Test
    fun `yesterday window is one day back`() {
        val now = System.currentTimeMillis()
        val since = AssistantQuery.sinceTimestamp("was gab es gestern", now)
        val days = (now - since) / (24L * 60 * 60 * 1000)
        assertTrue("days=$days", days in 0..2)
    }
}
