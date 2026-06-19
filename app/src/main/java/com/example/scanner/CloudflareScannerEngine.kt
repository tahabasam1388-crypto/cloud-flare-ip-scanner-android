package com.example.scanner

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random
import kotlin.system.measureTimeMillis

data class CloudflareIp(
    val ip: String,
    val port: Int = 443
)

data class ScanResult(
    val ip: String,
    val port: Int,
    val latency: Long, // ms
    val speed: Double,  // MB/s
    val isClean: Boolean
)

object CloudflareScannerEngine {

    // Official Cloudflare IPv4 Subnet/CIDR Range database
    val cloudflareSubnets = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.159.0.0/16",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22"
    )

    private val trustAllSslSocketFactory by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                }
            )
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            sc.socketFactory
        } catch (e: Exception) {
            SSLSocketFactory.getDefault() as SSLSocketFactory
        }
    }


    fun getDefaultHosts(): List<String> = cloudflareSubnets

    /**
     * Converts an IP string to a 32-bit Integer
     */
    private fun ipToLong(ipAddress: String): Long {
        val parts = ipAddress.split(".")
        if (parts.size != 4) return 0L
        var num = 0L
        for (i in 0..3) {
            val power = 3 - i
            val octet = parts[i].toLongOrNull() ?: 0L
            num += octet shl (power * 8)
        }
        return num
    }

    /**
     * Converts a 32-bit Integer to dotted-quad IP notation
     */
    private fun longToIp(ipLong: Long): String {
        return "${(ipLong ushr 24) and 0xFF}.${(ipLong ushr 16) and 0xFF}.${(ipLong ushr 8) and 0xFF}.${ipLong and 0xFF}"
    }

    /**
     * Generates a massive pool of random, authentic and unique Cloudflare IPs
     */
    fun generateIpPool(count: Int): List<CloudflareIp> {
        val rangeSpecs = cloudflareSubnets.mapNotNull { cidr ->
            val parts = cidr.split("/")
            if (parts.size == 2) {
                val baseIp = parts[0]
                val prefix = parts[1].toIntOrNull() ?: 24
                val start = ipToLong(baseIp)
                val totalAddresses = 1L shl (32 - prefix)
                val end = start + totalAddresses - 1
                Triple(start, end, totalAddresses)
            } else null
        }

        if (rangeSpecs.isEmpty()) return emptyList()

        val results = mutableSetOf<String>()
        val totalWeights = rangeSpecs.sumOf { it.third }

        // Generate matching unique IPs based on subnet weights
        var attempts = 0
        val targetSize = count.coerceIn(10, 100000)
        while (results.size < targetSize && attempts < targetSize * 3) {
            attempts++
            // weighted selection of subnet
            val randomWeight = Random.nextLong(0, totalWeights)
            var currentWeightSum = 0L
            var selectedRange = rangeSpecs.first()
            for (spec in rangeSpecs) {
                currentWeightSum += spec.third
                if (randomWeight < currentWeightSum) {
                    selectedRange = spec
                    break
                }
            }

            val randomIpLong = Random.nextLong(selectedRange.first, selectedRange.second + 1)
            results.add(longToIp(randomIpLong))
        }

        // Fallback or fill if set size isn't enough
        if (results.size < targetSize) {
            for (spec in rangeSpecs) {
                if (results.size >= targetSize) break
                val step = (spec.second - spec.first) / 10L
                val actualStep = if (step > 0) step else 1L
                var current = spec.first
                while (current <= spec.second && results.size < targetSize) {
                    results.add(longToIp(current))
                    current += actualStep
                }
            }
        }

        return results.map { CloudflareIp(it) }
    }

    /**
     * Converts a list of CIDR strings or individual IPs into sampled IP targets.
     * Unlike the old rigid logic that always generated exactly 206 IPs, this now
     * scales dynamically and samples evenly to match the user's selected pool size.
     */
    fun expandCidrOrIpList(input: String, generatePoolSize: Int = 1000): List<CloudflareIp> {
        val lines = input.split(Regex("[,\n\\s]")).map { it.trim() }.filter { it.isNotEmpty() }
        val staticIps = mutableSetOf<String>()
        val ranges = mutableListOf<Pair<Long, Long>>()

        for (item in lines) {
            if (item.contains("/")) {
                val parts = item.split("/")
                if (parts.size == 2) {
                    val baseIp = parts[0]
                    val mask = parts[1].toIntOrNull() ?: 24
                    val start = ipToLong(baseIp)
                    val size = 1L shl (32 - mask)
                    val end = start + size - 1
                    ranges.add(Pair(start, end))
                }
            } else if (item.split(".").size == 4) {
                staticIps.add(item)
            }
        }

        // If no custom CIDRs and no static IPs are specified, fallback to default subnets
        if (ranges.isEmpty() && staticIps.isEmpty()) {
            cloudflareSubnets.forEach { cidr ->
                val parts = cidr.split("/")
                if (parts.size == 2) {
                    val baseIp = parts[0]
                    val mask = parts[1].toIntOrNull() ?: 24
                    val start = ipToLong(baseIp)
                    val size = 1L shl (32 - mask)
                    val end = start + size - 1
                    ranges.add(Pair(start, end))
                }
            }
        }

        val results = mutableSetOf<String>()
        results.addAll(staticIps)

        val targetSize = generatePoolSize.coerceIn(10, 100000)

        if (ranges.isNotEmpty()) {
            var attempts = 0
            val maxAttempts = targetSize * 3
            while (results.size < targetSize && attempts < maxAttempts) {
                attempts++
                val range = ranges.random()
                val randomIpLong = if (range.second > range.first) {
                    Random.nextLong(range.first, range.second + 1)
                } else {
                    range.first
                }
                results.add(longToIp(randomIpLong))
            }
        }

        return results.map { CloudflareIp(it) }
    }

    /**
     * Measure the Socket response latency and estimate potential speed.
     * Raw TCP latency connect is measured first to return accurate physical ping times.
     * Subsequently, if it is a secure TLS port (like 443), we perform an SNI-injected TLS handshake
     * to verify the IP is clean and active, without inflating the ping latency.
     */
    suspend fun scanIp(cfIp: CloudflareIp, timeoutMs: Int, sniHost: String? = null): ScanResult = withContext(Dispatchers.IO) {
        var latency = -1L
        var isClean = false

        val securePorts = listOf(443, 2053, 2083, 2087, 2096, 8443)
        val isTlsPort = cfIp.port in securePorts

        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            val address = InetSocketAddress(cfIp.ip, cfIp.port)
            rawSocket = Socket()
            
            // Measure direct physical TCP RTT connect latency first (standard accurate ping)
            latency = measureTimeMillis {
                rawSocket.connect(address, timeoutMs)
            }
            isClean = true

            if (isTlsPort) {
                var handshakeSuccess = false
                
                // If we have a custom sniHost, try handshaking with it first
                if (!sniHost.isNullOrEmpty() && sniHost != "cloudflare.com") {
                    try {
                        sslSocket = trustAllSslSocketFactory.createSocket(
                            rawSocket,
                            cfIp.ip,
                            cfIp.port,
                            true
                        ) as SSLSocket
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val sslParams = sslSocket.sslParameters
                            sslParams.serverNames = listOf(javax.net.ssl.SNIHostName(sniHost))
                            sslSocket.sslParameters = sslParams
                        }
                        sslSocket.soTimeout = timeoutMs
                        sslSocket.startHandshake()
                        handshakeSuccess = true
                    } catch (e: Exception) {
                        handshakeSuccess = false
                        // Close these and try a fallback connection to verify if the IP is clean
                        try { sslSocket?.close() } catch (ignored: Exception) {}
                        try { rawSocket?.close() } catch (ignored: Exception) {}
                        sslSocket = null
                        rawSocket = null
                    }
                }
                
                // Fallback to handshake if custom handshake wasn't done, or if it failed
                if (!handshakeSuccess) {
                    rawSocket = Socket()
                    var connectLatency = 0L
                    connectLatency = measureTimeMillis {
                        rawSocket.connect(address, timeoutMs)
                    }
                    sslSocket = trustAllSslSocketFactory.createSocket(
                        rawSocket,
                        cfIp.ip,
                        cfIp.port,
                        true
                    ) as SSLSocket
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try {
                            val sslParams = sslSocket.sslParameters
                            val fallbackSni = if (!sniHost.isNullOrEmpty()) sniHost else "cloudflare.com"
                            sslParams.serverNames = listOf(javax.net.ssl.SNIHostName(fallbackSni))
                            sslSocket.sslParameters = sslParams
                        } catch (ignored: Exception) {}
                    }
                    sslSocket.soTimeout = timeoutMs
                    sslSocket.startHandshake()
                    handshakeSuccess = true
                    
                    // Since fallback did a new connection, latency is the physical TCP connection time of this fallback connection
                    latency = connectLatency
                }
            }
        } catch (e: Exception) {
            isClean = false
            latency = 9999L
        } finally {
            try { sslSocket?.close() } catch (ignored: Exception) {}
            try { rawSocket?.close() } catch (ignored: Exception) {}
        }

        ScanResult(
            ip = cfIp.ip,
            port = cfIp.port,
            latency = if (isClean) latency else 9999L,
            speed = 0.0,
            isClean = isClean
        )
    }

    /**
     * Conducts a genuine HTTP/1.1 or TLS speed download test over cdnjs.cloudflare.com
     * routed directly through the targeted Cloudflare IP. cdnjs.cloudflare.com is hosted 
     * on Cloudflare's CDN network and is 100% unblocked in Iran, ensuring realistic measurements.
     * This measures actual bytes read per millisecond, translating to authentic MB/s transfer speed.
     */
    suspend fun testActualSpeed(ip: String, port: Int, timeoutMs: Int): Double = withContext(Dispatchers.IO) {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            val address = InetSocketAddress(ip, port)
            rawSocket = Socket()
            rawSocket.connect(address, timeoutMs)

            val securePorts = listOf(443, 2053, 2083, 2087, 2096, 8443)
            val isTls = port in securePorts

            val inputStream = if (isTls) {
                sslSocket = trustAllSslSocketFactory.createSocket(
                    rawSocket,
                    ip,
                    port,
                    true
                ) as SSLSocket
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        val sslParams = sslSocket.sslParameters
                        sslParams.serverNames = listOf(javax.net.ssl.SNIHostName("cdnjs.cloudflare.com"))
                        sslSocket.sslParameters = sslParams
                    } catch (ignored: Exception) {}
                }
                sslSocket.soTimeout = timeoutMs
                sslSocket.startHandshake()
                sslSocket.inputStream
            } else {
                rawSocket.soTimeout = timeoutMs
                rawSocket.inputStream
            }

            val outputStream = if (isTls) sslSocket!!.outputStream else rawSocket.outputStream

            // Request 600 KB library (three.min.js) from Cloudflare cdnjs CDN
            val request = "GET /ajax/libs/three.js/r128/three.min.js HTTP/1.1\r\n" +
                    "Host: cdnjs.cloudflare.com\r\n" +
                    "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n" +
                    "Accept: */*\r\n" +
                    "Connection: close\r\n\r\n"

            outputStream.write(request.toByteArray(Charsets.US_ASCII))
            outputStream.flush()

            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            val startTime = System.currentTimeMillis()
            var bodyStarted = false

            while (true) {
                val now = System.currentTimeMillis()
                if (now - startTime > 3000) { // Max 3 seconds per IP to keep testing performant and avoid UI/coroutine freezing
                    break
                }
                val read = inputStream.read(buffer)
                if (read == -1) break

                if (!bodyStarted) {
                    // Quick ASCII search for double CRLF terminating headers
                    val str = String(buffer, 0, read, Charsets.US_ASCII)
                    val bodyIndex = str.indexOf("\r\n\r\n")
                    if (bodyIndex != -1) {
                        bodyStarted = true
                        val headersLength = bodyIndex + 4
                        totalBytesRead += (read - headersLength)
                    }
                } else {
                    totalBytesRead += read
                }
            }

            val durationMs = System.currentTimeMillis() - startTime
            if (totalBytesRead <= 0 || durationMs < 50L) {
                return@withContext 0.0
            }

            // Calculate MB/s: Megabytes per second
            // bytes / (1024 * 1024) divided by seconds (durationMs / 1000.0)
            val speedMbs = (totalBytesRead.toDouble() / (1024.0 * 1024.0)) / (durationMs.toDouble() / 1000.0)
            val result = if (speedMbs > 150.0) 0.0 else speedMbs // Filter out anomaly/socket caching
            return@withContext result
        } catch (e: Exception) {
            return@withContext 0.0
        } finally {
            try { sslSocket?.close() } catch (ignored: Exception) {}
            try { rawSocket?.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun runSubnetScan(
        ips: List<CloudflareIp>,
        timeoutMs: Int,
        maxConcurrency: Int,
        sniHost: String? = null,
        onProgress: (scanned: Int, total: Int, latestResults: List<ScanResult>) -> Unit
    ): List<ScanResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<ScanResult>()
        val total = ips.size
        var scannedCount = 0

        ips.chunked(maxConcurrency).forEach { chunk ->
            val jobs = chunk.map { ip ->
                async { scanIp(ip, timeoutMs, sniHost) }
            }
            val chunkResults = jobs.awaitAll()
            results.addAll(chunkResults)
            
            scannedCount += chunk.size
            val cleanResults = chunkResults.filter { it.isClean }
            onProgress(scannedCount, total, cleanResults)
        }

        results.filter { it.isClean }.sortedBy { it.latency }
    }

    fun isIpInCidr(ip: String, cidr: String): Boolean {
        try {
            val parts = cidr.split("/")
            if (parts.size != 2) return false
            val baseIp = parts[0]
            val maskBits = parts[1].toIntOrNull() ?: 24
            
            val ipLong = ipToLong(ip)
            val baseLong = ipToLong(baseIp)
            
            if (ipLong == 0L || baseLong == 0L) return false
            
            val mask = if (maskBits == 0) 0L else (-1L shl (32 - maskBits)) and 0xFFFFFFFFL
            return (ipLong and mask) == (baseLong and mask)
        } catch (e: Exception) {
            return false
        }
    }

    fun isCloudflareIp(ip: String): Boolean {
        return cloudflareSubnets.any { cidr -> isIpInCidr(ip, cidr) }
    }

    suspend fun isCloudflareHostOrIp(hostOrIp: String): Boolean = withContext(Dispatchers.IO) {
        if (hostOrIp.isBlank()) return@withContext false
        val cleanHost = hostOrIp.trim().lowercase(java.util.Locale.US)
        
        // Quick string check
        if (cleanHost.contains("cloudflare")) {
            return@withContext true
        }
        
        // If it's a domain/IP, try to resolve it and check the resulting IP addresses
        try {
            val addresses = java.net.InetAddress.getAllByName(cleanHost)
            for (address in addresses) {
                val ip = address.hostAddress ?: continue
                if (isCloudflareIp(ip)) {
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            // Fallback: If DNS resolution fails, check if string itself matches IPv4 format
            if (cleanHost.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))) {
                return@withContext isCloudflareIp(cleanHost)
            }
        }
        return@withContext false
    }
}
