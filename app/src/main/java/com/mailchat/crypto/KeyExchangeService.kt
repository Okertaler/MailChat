package com.mailchat.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object KeyExchangeService {

    /**
     * Generate a QR bitmap for the own public key. Returns null if the key is missing or the
     * payload is too large for a QR code (e.g. very long email + RSA-3072 key).
     */
    fun generateQrBitmap(context: Context, email: String, size: Int = 512): Bitmap? {
        return try {
            val data = OpenPgpKeyService.getPublicKeyForQr(context, email)
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                data, BarcodeFormat.QR_CODE, size, size
            )
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun createNdefMessage(context: Context, email: String): NdefMessage {
        val data = OpenPgpKeyService.getPublicKeyForQr(context, email)
        val payload = data.toByteArray(Charsets.UTF_8)
        val record = NdefRecord.createApplicationRecord("com.mailchat.pgp")
        val ndefMessage = NdefMessage(arrayOf(record))
        return ndefMessage
    }

    fun writeNfcTag(tag: Ndef, context: Context, email: String): Boolean {
        return try {
            tag.connect()
            val data = OpenPgpKeyService.getPublicKeyForQr(context, email)
            val payload = data.toByteArray(Charsets.UTF_8)
            val record = NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                "android.com:pkg".toByteArray(Charsets.US_ASCII),
                "com.mailchat".toByteArray(Charsets.US_ASCII),
                payload
            )
            tag.writeNdefMessage(NdefMessage(arrayOf(record)))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun parseNfcPayload(payload: ByteArray): Triple<String, String, String>? {
        return try {
            val data = String(payload, Charsets.UTF_8)
            OpenPgpKeyService.parseQrData(data)
        } catch (e: Exception) {
            null
        }
    }

    fun parseQrScanResult(result: String): Triple<String, String, String>? {
        return OpenPgpKeyService.parseQrData(result)
    }

    fun isNfcAvailable(context: Context): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return nfcAdapter != null && nfcAdapter.isEnabled
    }
}
