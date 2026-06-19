package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SavedIpEntity
import com.example.scanner.CloudflareIp
import com.example.scanner.CloudflareScannerEngine
import com.example.scanner.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface ScanUiState {
    object Idle : ScanUiState
    data class Scanning(
        val scanned: Int,
        val total: Int,
        val foundClean: Int,
        val progressPercent: Float
    ) : ScanUiState
    data class Finished(val totalScanned: Int, val cleanFound: Int) : ScanUiState
}

enum class LiveEventType {
    INFO, SUCCESS, SCAN, DATABASE, COPY, ERROR
}

data class LiveEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val messageEn: String,
    val messageFa: String,
    val type: LiveEventType = LiveEventType.INFO,
    val timestamp: Long = System.currentTimeMillis()
)

data class VlessTrojanConfig(
    val raw: String,
    val scheme: String,
    val credentials: String,
    val host: String,
    val port: Int,
    val query: String,
    val remark: String
) {
    fun copyWithIp(ip: String): String {
        var updatedQuery = query
        if (isValidDomain(host)) {
            val params = if (query.isEmpty()) emptyList<String>() else query.split("&")
            val hasSni = params.any { it.trim().substringBefore("=").lowercase(Locale.US) == "sni" }
            val hasHost = params.any { it.trim().substringBefore("=").lowercase(Locale.US) == "host" }
            
            val addedParams = mutableListOf<String>()
            if (!hasSni) {
                addedParams.add("sni=$host")
            }
            if (!hasHost) {
                addedParams.add("host=$host")
            }
            
            if (addedParams.isNotEmpty()) {
                val suffix = addedParams.joinToString("&")
                updatedQuery = if (query.isEmpty()) suffix else "$query&$suffix"
            }
        }
        val queryPart = if (updatedQuery.isNotEmpty()) "?$updatedQuery" else ""
        val remarkPart = if (remark.isNotEmpty()) "#$remark" else ""
        return "$scheme://$credentials@$ip:$port$queryPart$remarkPart"
    }

    fun getSslSni(): String? {
        if (query.isEmpty()) return null
        val params = query.split("&").associate {
            val parts = it.split("=", limit = 2)
            val key = parts.getOrNull(0)?.lowercase(Locale.US)?.trim() ?: ""
            val value = parts.getOrNull(1)?.trim() ?: ""
            key to value
        }
        val sniValue = params["sni"] ?: params["host"]
        if (!sniValue.isNullOrEmpty()) {
            return sniValue
        }
        return null
    }

    fun isValidDomain(str: String): Boolean {
        if (str.isEmpty()) return false
        if (str.contains(":")) return false
        if (str.all { it.isDigit() || it == '.' }) return false
        return true
    }

    fun getBestSni(): String? {
        val qSni = getSslSni()
        if (!qSni.isNullOrEmpty()) return qSni
        if (isValidDomain(host)) return host
        return null
    }
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.savedIpDao()
    
    var isPersianLanguage: Boolean = false

    private val _liveEvents = MutableStateFlow<List<LiveEvent>>(emptyList())
    val liveEvents: StateFlow<List<LiveEvent>> = _liveEvents.asStateFlow()

    fun logEvent(messageEn: String, messageFa: String, type: LiveEventType = LiveEventType.INFO) {
        viewModelScope.launch(Dispatchers.Main) {
            val newEvent = LiveEvent(messageEn = messageEn, messageFa = messageFa, type = type)
            _liveEvents.value = (_liveEvents.value + newEvent).takeLast(60)
        }
    }
    // Db Flow containing all saved top speed clean IPs
    val savedIps: StateFlow<List<SavedIpEntity>> = dao.getAllSavedIps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _ipInputList = MutableStateFlow("")
    val ipInputList = _ipInputList.asStateFlow()

    private val _timeoutMs = MutableStateFlow(1500)
    val timeoutMs = _timeoutMs.asStateFlow()

    private val _concurrency = MutableStateFlow(15)
    val concurrency = _concurrency.asStateFlow()

    // Dynamic Cloudflare IP Pool generator size (Default 1000, can handle up to 50000+)
    private val _ipPoolSizeToGenerate = MutableStateFlow(1000)
    val ipPoolSizeToGenerate = _ipPoolSizeToGenerate.asStateFlow()

    // --- VLESS / TROJAN PROFILE SUPPORT ---
    private val _configInput = MutableStateFlow("")
    val configInput = _configInput.asStateFlow()

    private val _isCloudflareConfig = MutableStateFlow<Boolean?>(null)
    val isCloudflareConfig = _isCloudflareConfig.asStateFlow()

    private var detectionJob: Job? = null

    private fun detectCloudflareNetwork(configText: String) {
        detectionJob?.cancel()
        if (configText.isBlank()) {
            _isCloudflareConfig.value = null
            return
        }
        detectionJob = viewModelScope.launch(Dispatchers.Default) {
            val parsed = parseVlessTrojanConfig(configText)
            if (parsed == null) {
                _isCloudflareConfig.value = null
            } else {
                val isCf = CloudflareScannerEngine.isCloudflareHostOrIp(parsed.host)
                _isCloudflareConfig.value = isCf
            }
        }
    }

    // --- CUSTOM MAIN PORT SCANNER ---
    private val _customPort = MutableStateFlow(443)
    val customPort = _customPort.asStateFlow()

    private val _scanUiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanUiState = _scanUiState.asStateFlow()

    private val _isSpeedTestEnabled = MutableStateFlow(true)
    val isSpeedTestEnabled = _isSpeedTestEnabled.asStateFlow()

    private val _speedTestLimit = MutableStateFlow(10)
    val speedTestLimit = _speedTestLimit.asStateFlow()

    // Temporary list of scanned results during the lifetime of current execution session
    private val _activeScanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val activeScanResults = _activeScanResults.asStateFlow()

    // Scanning Job Reference for cancellation
    private var scanJob: Job? = null

    // Set to prevent duplicate concurrent speed download tasks
    private val speedTestingIps = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        // Populate default IP guidelines list to encourage prompt action
        val defaultHostsBuilder = StringBuilder()
        CloudflareScannerEngine.getDefaultHosts().forEach {
            defaultHostsBuilder.append(it).append("\n")
        }
        _ipInputList.value = defaultHostsBuilder.toString()
    }

    fun parseVlessTrojanConfig(uriString: String): VlessTrojanConfig? {
        try {
            val trimmed = uriString.trim()
            if (trimmed.isEmpty()) return null
            
            val schemeSeparator = "://"
            if (!trimmed.contains(schemeSeparator)) return null
            
            val scheme = trimmed.substringBefore(schemeSeparator).lowercase(Locale.US)
            val rest = trimmed.substringAfter(schemeSeparator)
            
            if (!rest.contains("@")) return null
            val credentials = rest.substringBeforeLast("@")
            val addressAndParams = rest.substringAfterLast("@")
            
            val remark = if (addressAndParams.contains("#")) {
                addressAndParams.substringAfter("#")
            } else ""
            
            val withoutRemark = addressAndParams.substringBefore("#")
            
            val query = if (withoutRemark.contains("?")) {
                withoutRemark.substringAfter("?")
            } else ""
            
            val addressAndPortOnly = withoutRemark.substringBefore("?")
            
            val host: String
            val port: Int
            if (addressAndPortOnly.contains(":")) {
                host = addressAndPortOnly.substringBeforeLast(":")
                val portStr = addressAndPortOnly.substringAfterLast(":")
                port = portStr.toIntOrNull() ?: 443
            } else {
                host = addressAndPortOnly
                port = 443
            }
            
            return VlessTrojanConfig(
                raw = trimmed,
                scheme = scheme,
                credentials = credentials,
                host = host,
                port = port,
                query = query,
                remark = remark
            )
        } catch (e: Exception) {
            return null
        }
    }

    fun updateInputList(value: String) {
        _ipInputList.value = value
    }

    fun updateTimeout(ms: Int) {
        _timeoutMs.value = ms
    }

    fun updateConcurrency(count: Int) {
        _concurrency.value = count
    }

    fun updateIpPoolSize(size: Int) {
        _ipPoolSizeToGenerate.value = size
    }

    fun updateConfigInput(value: String) {
        _configInput.value = value
        detectCloudflareNetwork(value)
    }

    fun updateCustomPort(port: Int) {
        _customPort.value = port
    }

    fun updateSpeedTestEnabled(enabled: Boolean) {
        _isSpeedTestEnabled.value = enabled
        reapplySpeedLimits()
    }

    fun updateSpeedTestLimit(limit: Int) {
        _speedTestLimit.value = limit
        reapplySpeedLimits()
    }

    private fun updateIpSpeedState(ip: String, speed: Double) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentList = _activeScanResults.value.toMutableList()
            val index = currentList.indexOfFirst { it.ip == ip }
            if (index != -1) {
                currentList[index] = currentList[index].copy(speed = speed)
                _activeScanResults.value = currentList
            }
        }
    }

    private fun runBackgroundSpeedTest(item: ScanResult) {
        val ip = item.ip
        if (!speedTestingIps.add(ip)) {
            return // Already testing or tested
        }

        // Mark as testing (-1.0) in our active results state flow
        updateIpSpeedState(ip, -1.0)

        viewModelScope.launch(Dispatchers.Default) {
            logEvent(
                "Starting actual speed test for $ip via unblocked speedtest/cdnjs servers...",
                "در حال تست سرعت واقعی آی‌پی $ip از طریق CDN کلادفلر بدون فیلتر...",
                LiveEventType.SCAN
            )

            // Perform the heavy network request using CloudflareScannerEngine
            val realSpeed = CloudflareScannerEngine.testActualSpeed(
                ip = ip,
                port = item.port,
                timeoutMs = _timeoutMs.value.coerceAtLeast(3000) // Ensure enough time for a solid download
            )

            // Speed test completed. Show in log and update state
            updateIpSpeedState(ip, realSpeed)

            logEvent(
                "Real speed test finished for $ip: ${String.format(Locale.US, "%.2f", realSpeed)} MB/s",
                "تست سرعت واقعی برای $ip پایان یافت: ${String.format(Locale.US, "%.2f", realSpeed)} مگابایت بر ثانیه",
                LiveEventType.SCAN
            )

            // Save/update this record in Room database
            viewModelScope.launch(Dispatchers.IO) {
                dao.insertIp(
                    SavedIpEntity(
                        ip = ip,
                        latency = item.latency,
                        speed = realSpeed,
                        port = item.port
                    )
                )
            }
        }
    }

    private fun reapplySpeedLimits() {
        val current = _activeScanResults.value
        if (current.isEmpty()) return
        val speedEnabled = _isSpeedTestEnabled.value
        val limit = _speedTestLimit.value
        
        val sortedList = current.sortedBy { it.latency }
        val updatedList = sortedList.mapIndexed { index, item ->
            val shouldHaveSpeed = speedEnabled && index < limit
            if (shouldHaveSpeed) {
                if (item.speed <= 0.0 && item.speed != -1.0) {
                    runBackgroundSpeedTest(item)
                    item.copy(speed = -1.0)
                } else {
                    item
                }
            } else {
                if (item.speed > 0.0 && item.speed != -1.0) {
                    item.copy(speed = 0.0)
                } else {
                    item
                }
            }
        }
        _activeScanResults.value = updatedList
    }

    fun runManualSpeedTest(ip: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentList = _activeScanResults.value.toMutableList()
            val index = currentList.indexOfFirst { it.ip == ip }
            if (index != -1) {
                val originalItem = currentList[index]
                if (speedTestingIps.contains(ip)) return@launch // Already running
                
                // Mark as testing
                currentList[index] = originalItem.copy(speed = -1.0)
                _activeScanResults.value = currentList
                speedTestingIps.add(ip)
                
                logEvent(
                    "Starting manual speed test for $ip...",
                    "شروع تست سرعت دستی برای $ip...",
                    LiveEventType.SCAN
                )
                
                viewModelScope.launch(Dispatchers.Default) {
                    val realSpeed = CloudflareScannerEngine.testActualSpeed(
                        ip = originalItem.ip,
                        port = originalItem.port,
                        timeoutMs = _timeoutMs.value.coerceAtLeast(3000)
                    )
                    
                    val updatedList = _activeScanResults.value.toMutableList()
                    val idx = updatedList.indexOfFirst { it.ip == ip }
                    if (idx != -1) {
                        val updatedItem = updatedList[idx].copy(speed = realSpeed)
                        updatedList[idx] = updatedItem
                        _activeScanResults.value = updatedList

                        // Synchronize to DB
                        viewModelScope.launch(Dispatchers.IO) {
                            dao.insertIp(
                                SavedIpEntity(
                                    ip = updatedItem.ip,
                                    latency = updatedItem.latency,
                                    speed = updatedItem.speed,
                                    port = updatedItem.port
                                )
                            )
                        }
                        
                        logEvent(
                            "Manual speed test complete for $ip: ${String.format(Locale.US, "%.2f", realSpeed)} MB/s",
                            "تست سرعت دستی برای $ip خاتمه یافت: ${String.format(Locale.US, "%.2f", realSpeed)} مگابایت بر ثانیه",
                            LiveEventType.SCAN
                        )
                    }
                }
            }
        }
    }

    fun startScanning() {
        // Cancel any current job
        scanJob?.cancel()
        speedTestingIps.clear()

        val rawInput = _ipInputList.value
        val poolSize = _ipPoolSizeToGenerate.value
        
        // Pick proper port based on config selection status
        val parsed = parseVlessTrojanConfig(_configInput.value)
        val targetPort = parsed?.port ?: _customPort.value

        val rawTargets = CloudflareScannerEngine.expandCidrOrIpList(rawInput, poolSize)
        val targets = rawTargets.map { it.copy(port = targetPort) }
        
        _activeScanResults.value = emptyList()
        val totalTargets = targets.size
        _scanUiState.value = ScanUiState.Scanning(
            scanned = 0,
            total = totalTargets,
            foundClean = 0,
            progressPercent = 0f
        )

        logEvent(
            "Starting live scan over $totalTargets candidates on Port $targetPort...",
            "شروع اسکن زنده روی $totalTargets کاندیدا روی پورت $targetPort...",
            LiveEventType.SCAN
        )

        scanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                CloudflareScannerEngine.runSubnetScan(
                    ips = targets,
                    timeoutMs = _timeoutMs.value,
                    maxConcurrency = _concurrency.value,
                    sniHost = parsed?.getBestSni()
                ) { scanned, total, latestResults ->
                    val activeCleanList = _activeScanResults.value.toMutableList()
                    latestResults.forEach { item ->
                        activeCleanList.add(item)
                        logEvent(
                            "Clean IP found: ${item.ip} (Latency: ${item.latency}ms)",
                            "آی‌پی تمیز پیدا شد: ${item.ip} (تاخیر: ${item.latency} میلی‌ثانیه)",
                            LiveEventType.SCAN
                        )
                        viewModelScope.launch(Dispatchers.IO) {
                            dao.insertIp(
                                SavedIpEntity(
                                    ip = item.ip,
                                    latency = item.latency,
                                    speed = item.speed,
                                    port = item.port
                                )
                            )
                        }
                    }
                    
                    // Sort active scan results immediately by latency ascending (best speed indicators first)
                    val rawCleanList = activeCleanList.distinctBy { it.ip }.sortedBy { it.latency }
                    
                    val speedEnabled = _isSpeedTestEnabled.value
                    val limit = _speedTestLimit.value
                    
                    val sortedActive = rawCleanList.mapIndexed { index, item ->
                        val shouldHaveSpeed = speedEnabled && index < limit
                        if (shouldHaveSpeed) {
                            if (item.speed <= 0.0 && item.speed != -1.0) {
                                runBackgroundSpeedTest(item)
                                item.copy(speed = -1.0)
                            } else {
                                item
                            }
                        } else {
                            if (item.speed > 0.0 && item.speed != -1.0) {
                                item.copy(speed = 0.0)
                            } else {
                                item
                            }
                        }
                    }
                    _activeScanResults.value = sortedActive

                    viewModelScope.launch(Dispatchers.Main) {
                        _scanUiState.value = ScanUiState.Scanning(
                            scanned = scanned,
                            total = total,
                            foundClean = sortedActive.size,
                            progressPercent = scanned.toFloat() / total.toFloat()
                        )
                    }

                    // Post system notification regarding active progress
                    com.example.scanner.NotificationHelper.showScanProgressNotification(
                        getApplication(),
                        scanned,
                        total,
                        sortedActive.size,
                        isPersianLanguage
                    )
                }

                viewModelScope.launch(Dispatchers.Main) {
                    val cleanFoundCount = _activeScanResults.value.size
                    logEvent(
                        "Scan completed! Scanned $totalTargets hosts and discovered $cleanFoundCount clean IPs.",
                        "اسکن به پایان رسید! $totalTargets کاندیدا بررسی شد و $cleanFoundCount آی‌پی تمیز به دست آمد.",
                        LiveEventType.SUCCESS
                    )
                    _scanUiState.value = ScanUiState.Finished(
                        totalScanned = totalTargets,
                        cleanFound = cleanFoundCount
                    )
                    
                    // Post system notification regarding completion
                    com.example.scanner.NotificationHelper.showScanCompleteNotification(
                        getApplication(),
                        totalTargets,
                        cleanFoundCount,
                        isPersianLanguage
                    )
                }
            } catch (e: Exception) {
                // Job cancelled or error encountered
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        val currentClean = _activeScanResults.value.size
        logEvent(
            "Scanner matching operation halted by user. Retained $currentClean discovered IPs.",
            "فرآیند اسکن بنا به درخواست کاربر متوقف شد. تعداد $currentClean آی‌پی ارزیابی شده حفظ شدند.",
            LiveEventType.INFO
        )
        _scanUiState.value = ScanUiState.Finished(
            totalScanned = currentClean, // stopped, so reflect what we have
            cleanFound = currentClean
        )
        com.example.scanner.NotificationHelper.dismissNotification(getApplication())
    }

    fun saveIpToHistory(ip: String, latency: Long, speed: Double, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertIp(SavedIpEntity(ip = ip, latency = latency, speed = speed, port = port))
        }
        logEvent(
            "Saved IP address $ip to cached clean database registry.",
            "آدرس آی‌پی تمیز $ip به صورت دستی در دیتابیس ثبت شد.",
            LiveEventType.DATABASE
        )
    }

    fun deleteSavedIp(entity: SavedIpEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteIp(entity)
        }
        logEvent(
            "Removed IP address ${entity.ip} from cached database store.",
            "آی‌پی تمیز ${entity.ip} با موفقیت از دیتابیس برداشته شد.",
            LiveEventType.DATABASE
        )
    }

    fun clearSavedHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAll()
        }
        logEvent(
            "Cleared entire offline records history from Local SQLite Store.",
            "تمامی تاریخچه و لیست آی‌پی‌های ذخیره شده به کلی از دیتابیس پاک شد.",
            LiveEventType.DATABASE
        )
    }
}
