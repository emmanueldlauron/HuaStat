package com.huastat

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class RouterStats(
    val rssi: Double?,
    val rsrq: Double?,
    val rsrp: Double?,
    val sinr: Double?,
    val band: String,
    val cellId: String,
    val enbId: String,
    val width: String,
    val isp: String,
    val network: String,
    val dlRate: Long,      // bytes/s
    val ulRate: Long,      // bytes/s
    val curDl: Long,       // bytes this connection
    val curUl: Long,
    val totalDl: Long,     // bytes all time
    val totalUl: Long,
    val curTime: Long,     // seconds
    val totalTime: Long
)

/**
 * Minimal client for the Huawei HiLink web API (B-series routers).
 * Reads signal, traffic and network info. Logs in only when the router asks for it.
 */
class RouterApi(
    private val host: String,
    private val user: String,
    private val pass: String
) {
    private class Resp(val body: String, val headers: Map<String?, List<String>>) {
        fun header(name: String): List<String> =
            headers.entries.firstOrNull { it.key?.equals(name, true) == true }?.value ?: emptyList()
    }

    private var cookie = ""
    private var token = ""

    private fun call(path: String, body: String? = null): Resp {
        val conn = URL("http://$host$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 4000
        if (cookie.isNotEmpty()) conn.setRequestProperty("Cookie", cookie)
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            if (token.isNotEmpty()) conn.setRequestProperty("__RequestVerificationToken", token)
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code < 400) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val headers = conn.headerFields
        conn.disconnect()
        return Resp(text, headers)
    }

    private fun tag(xml: String, name: String): String? =
        Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

    private fun num(xml: String, name: String): Double? =
        tag(xml, name)?.let { Regex("-?\\d+(\\.\\d+)?").find(it)?.value?.toDoubleOrNull() }

    private fun long(xml: String, name: String): Long = tag(xml, name)?.toLongOrNull() ?: 0L

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun b64(s: String): String = Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)

    private fun newSession() {
        cookie = ""
        val r = call("/api/webserver/SesTokInfo")
        tag(r.body, "SesInfo")?.let { cookie = it }
        tag(r.body, "TokInfo")?.let { token = it }
    }

    private fun login() {
        newSession()
        val inner = b64(sha256Hex(pass))
        val outer = b64(sha256Hex(user + inner + token))
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request>" +
            "<Username>$user</Username><Password>$outer</Password>" +
            "<password_type>4</password_type></request>"
        val r = call("/api/user/login", body)
        if (r.body.contains("<error>")) {
            throw IllegalStateException("Login failed (code ${tag(r.body, "code")}). Check password in Settings.")
        }
        r.header("Set-Cookie").firstOrNull { it.contains("SessionID=") }
            ?.let { cookie = it.substringBefore(";") }
        (r.header("__RequestVerificationTokenone").firstOrNull()
            ?: r.header("__RequestVerificationToken").firstOrNull())
            ?.let { token = it }
    }

    private fun get(path: String): String {
        if (cookie.isEmpty()) newSession()
        var r = call(path)
        if (r.body.contains("<error>")) {
            if (pass.isEmpty()) {
                throw IllegalStateException("Router wants a login. Enter your password in Settings.")
            }
            login()
            r = call(path)
            if (r.body.contains("<error>")) {
                throw IllegalStateException("Router error ${tag(r.body, "code")} on $path")
            }
        }
        return r.body
    }

    private val bandMhz = mapOf(
        "1" to 2100, "3" to 1800, "5" to 850, "7" to 2600, "8" to 900,
        "20" to 800, "28" to 700, "38" to 2600, "40" to 2300, "41" to 2500
    )

    fun fetch(): RouterStats {
        val sig = get("/api/device/signal")
        val tr = get("/api/monitoring/traffic-statistics")
        val plmn = runCatching { get("/api/net/current-plmn") }.getOrDefault("")
        val status = runCatching { get("/api/monitoring/status") }.getOrDefault("")

        val cell = tag(sig, "cell_id") ?: ""
        val enb = tag(sig, "enodeb_id")
            ?: cell.toLongOrNull()?.let { (it shr 8).toString() }
            ?: ""
        val bandNum = (tag(sig, "band") ?: "").filter { it.isDigit() }
        val bandText = if (bandNum.isEmpty()) "-" else
            "B$bandNum" + (bandMhz[bandNum]?.let { "-${it}MHz" } ?: "")
        val dlw = tag(sig, "dlbandwidth")
        val ulw = tag(sig, "ulbandwidth")
        val width = when {
            dlw != null && ulw != null -> "D:${dlw.removeSuffix("MHz")} | U:${ulw.removeSuffix("MHz")} MHz"
            else -> tag(sig, "bandwidth") ?: "-"
        }
        val net = when (tag(status, "CurrentNetworkTypeEx") ?: tag(status, "CurrentNetworkType")) {
            "1011" -> "LTE+"
            "101", "19" -> "LTE"
            null -> "-"
            else -> tag(status, "CurrentNetworkTypeEx") ?: "-"
        }

        return RouterStats(
            rssi = num(sig, "rssi"),
            rsrq = num(sig, "rsrq"),
            rsrp = num(sig, "rsrp"),
            sinr = num(sig, "sinr"),
            band = bandText,
            cellId = cell.ifEmpty { "-" },
            enbId = enb.ifEmpty { "-" },
            width = width,
            isp = tag(plmn, "FullName") ?: tag(plmn, "ShortName") ?: "-",
            network = net,
            dlRate = long(tr, "CurrentDownloadRate"),
            ulRate = long(tr, "CurrentUploadRate"),
            curDl = long(tr, "CurrentDownload"),
            curUl = long(tr, "CurrentUpload"),
            totalDl = long(tr, "TotalDownload"),
            totalUl = long(tr, "TotalUpload"),
            curTime = long(tr, "CurrentConnectTime"),
            totalTime = long(tr, "TotalConnectTime")
        )
    }
}
