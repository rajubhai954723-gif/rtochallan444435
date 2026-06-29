package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.LogEntity
import com.example.data.database.RuleEntity
import com.example.data.repository.SmsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SmsViewModel(
    application: Application,
    private val repository: SmsRepository
) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)

    // UI state flows
    private val _currentTab = MutableStateFlow(Tab.DASHBOARD)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _globalForwardingEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("global_forwarding_enabled", true)
    )
    val globalForwardingEnabled: StateFlow<Boolean> = _globalForwardingEnabled.asStateFlow()

    // Security state flows
    private val _isLocked = MutableStateFlow(sharedPrefs.contains("admin_passcode"))
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isPasscodeSet = MutableStateFlow(sharedPrefs.contains("admin_passcode"))
    val isPasscodeSet: StateFlow<Boolean> = _isPasscodeSet.asStateFlow()

    // Device status state flows
    private val _batteryLevel = MutableStateFlow(getBatteryPercentage())
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _connectionType = MutableStateFlow(getConnectionStatusName())
    val connectionType: StateFlow<String> = _connectionType.asStateFlow()

    // Observe rules from database
    val rules: StateFlow<List<RuleEntity>> = repository.allRules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Observe logs from database with real-time search filtering
    @OptIn(ExperimentalCoroutinesApi::class)
    val logs: StateFlow<List<LogEntity>> = _searchQuery
        .flatMapLatest { query ->
            repository.searchLogs(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setTab(tab: Tab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setGlobalForwarding(enabled: Boolean) {
        _globalForwardingEnabled.value = enabled
        sharedPrefs.edit().putBoolean("global_forwarding_enabled", enabled).apply()
    }

    // Passcode security methods
    fun setPasscode(passcode: String) {
        if (passcode.length == 4) {
            sharedPrefs.edit().putString("admin_passcode", passcode).apply()
            _isPasscodeSet.value = true
            _isLocked.value = false
        }
    }

    fun disablePasscode() {
        sharedPrefs.edit().remove("admin_passcode").apply()
        _isPasscodeSet.value = false
        _isLocked.value = false
    }

    fun verifyPasscode(passcode: String): Boolean {
        val saved = sharedPrefs.getString("admin_passcode", "")
        return if (saved == passcode) {
            _isLocked.value = false
            true
        } else {
            false
        }
    }

    fun lockApp() {
        if (_isPasscodeSet.value) {
            _isLocked.value = true
        }
    }

    // CRUD Rules
    fun addRule(rule: RuleEntity) {
        viewModelScope.launch {
            repository.insertRule(rule)
        }
    }

    fun updateRule(rule: RuleEntity) {
        viewModelScope.launch {
            repository.updateRule(rule)
        }
    }

    fun deleteRule(rule: RuleEntity) {
        viewModelScope.launch {
            repository.deleteRule(rule)
        }
    }

    // CRUD Logs
    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun deleteLogItem(logItem: LogEntity) {
        viewModelScope.launch {
            repository.deleteLog(logItem)
        }
    }

    // Mock incoming SMS triggering for easy testing & preview
    fun triggerMockSms(sender: String, message: String) {
        viewModelScope.launch {
            if (_globalForwardingEnabled.value) {
                repository.forwardSms(sender, message)
            } else {
                val bypassedLog = LogEntity(
                    sender = sender,
                    messageBody = message,
                    status = "FILTERED",
                    statusMessage = "Ignored: Global Forwarding Service is currently paused."
                )
                // Insert directly to database logs
                val db = AppDatabase.getInstance(getApplication())
                db.logDao().insertLog(bypassedLog)
            }
        }
    }

    // Device status helpers
    fun refreshDeviceStatus() {
        _batteryLevel.value = getBatteryPercentage()
        _connectionType.value = getConnectionStatusName()
    }

    private fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifre ->
            getApplication<Application>().registerReceiver(null, ifre)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            100
        }
    }

    private fun getConnectionStatusName(): String {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "Disconnected"
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return "Disconnected"
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular Data"
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    enum class Tab {
        DASHBOARD, RULES, LOGS, INTEGRATIONS
    }

    class Factory(
        private val application: Application,
        private val repository: SmsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SmsViewModel::class.java)) {
                return SmsViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
