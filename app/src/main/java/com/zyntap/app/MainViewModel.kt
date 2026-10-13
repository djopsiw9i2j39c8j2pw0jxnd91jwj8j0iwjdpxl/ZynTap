package com.zyntap.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zyntap.app.data.AdbPairing
import com.zyntap.app.data.AppEntry
import com.zyntap.app.data.AppRepository
import com.zyntap.app.data.PairState
import com.zyntap.app.data.PendingAdbPairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Home, Pair }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val pairing: AdbPairing = PendingAdbPairing()

    var screen by mutableStateOf(Screen.Home)
        private set
    var editMode by mutableStateOf(false)
        private set
    var pinned by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var pickerOpen by mutableStateOf(false)
        private set
    var allApps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var loadingApps by mutableStateOf(false)
        private set

    var code by mutableStateOf("")
        private set
    var pairState by mutableStateOf<PairState>(PairState.Idle)
        private set

    var message by mutableStateOf("")
        private set
    var messageVisible by mutableStateOf(false)
        private set

    val isPaired: Boolean get() = pairState is PairState.Success

    init {
        viewModelScope.launch {
            pinned = withContext(Dispatchers.Default) {
                repo.loadPinned().mapNotNull { repo.resolve(it) }
            }
        }
    }

    // ---- điều hướng -------------------------------------------------------
    fun openActivate() {
        editMode = false
        screen = Screen.Pair
    }

    fun back() {
        when {
            pickerOpen -> pickerOpen = false
            screen == Screen.Pair -> screen = Screen.Home
            editMode -> editMode = false
        }
    }

    fun toggleEditMode() {
        editMode = !editMode
    }

    // ---- danh sách app ----------------------------------------------------
    fun openPicker() {
        pickerOpen = true
        if (allApps.isEmpty()) {
            viewModelScope.launch {
                loadingApps = true
                allApps = withContext(Dispatchers.Default) { repo.launchable() }
                loadingApps = false
            }
        }
    }

    fun closePicker() {
        pickerOpen = false
    }

    fun isPinned(packageName: String): Boolean = pinned.any { it.packageName == packageName }

    fun toggle(entry: AppEntry) {
        if (isPinned(entry.packageName)) remove(entry.packageName) else add(entry)
    }

    private fun add(entry: AppEntry) {
        pinned = pinned + entry
        repo.savePinned(pinned.map { it.packageName })
    }

    fun remove(packageName: String) {
        pinned = pinned.filterNot { it.packageName == packageName }
        repo.savePinned(pinned.map { it.packageName })
    }

    fun launchApp(entry: AppEntry) {
        if (!repo.launch(entry.packageName)) show("Không mở được ${entry.label}")
    }

    // ---- ghép nối ---------------------------------------------------------
    fun onCode(value: String) {
        code = value.filter { it.isDigit() }.take(6)
        if (pairState is PairState.Failed) pairState = PairState.Idle
    }

    fun submitPair() {
        if (code.length != 6 || pairState is PairState.Working || pairState is PairState.Success) return
        pairState = PairState.Working
        viewModelScope.launch {
            val result = pairing.pair(code)
            if (result.isSuccess) {
                pairState = PairState.Success
                delay(900)
                screen = Screen.Home
            } else {
                pairState = PairState.Failed(result.exceptionOrNull()?.message ?: "Ghép nối thất bại")
            }
        }
    }

    // ---- thông báo nhỏ ----------------------------------------------------
    fun show(text: String) {
        message = text
        messageVisible = true
    }

    fun hideMessage() {
        messageVisible = false
    }
}
