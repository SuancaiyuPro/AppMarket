package com.app.market.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.download.DownloadPart
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.repository.DownloadRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FdroidApp(
    val packageName: String,
    val name: String,
    val summary: String,
    val description: String,
    val versionName: String,
    val versionCode: Long,
    val apkName: String,
    val size: Long,
    val sha256: String,
    val iconUrl: String,
    val iconFallbackUrls: List<String> = emptyList(),
)

data class FdroidUiState(
    val source: String = "official",
    val loading: Boolean = false,
    val error: String = "",
    val query: String = "",
    val apps: List<FdroidApp> = emptyList(),
    val selected: FdroidApp? = null,
) {
    val filteredApps: List<FdroidApp> get() = if (query.isBlank()) apps else apps.filter {
        it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) ||
            it.summary.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
    }
}

class FdroidViewModel(
    private val client: HttpClient,
    private val downloads: DownloadRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FdroidUiState())
    val uiState: StateFlow<FdroidUiState> = _uiState.asStateFlow()
    val downloadStates: StateFlow<Map<String, DownloadState>> = downloads.states
    private var loadGeneration = 0

    fun setQuery(value: String) = _uiState.update { it.copy(query = value) }
    fun select(app: FdroidApp?) = _uiState.update { it.copy(selected = app) }
    fun cancel(packageName: String) = downloads.cancel(packageName)
    fun installDownloaded(packageName: String) = downloads.install(packageName)

    fun load(source: String, force: Boolean = false) {
        if (!force && _uiState.value.source == source && _uiState.value.apps.isNotEmpty()) return
        val generation = ++loadGeneration
        _uiState.update { it.copy(source = source, loading = true, error = "", selected = null) }
        viewModelScope.launch {
            runCatching {
                val base = if (source == "tuna") TUNA_REPO else OFFICIAL_REPO
                val jar = client.get("${base}index-v1.jar").body<ByteArray>()
                withContext(Dispatchers.Default) { parseVerifiedFdroidIndexJar(jar, base) }
            }.onSuccess { apps ->
                if (generation == loadGeneration) _uiState.update { it.copy(loading = false, apps = apps, error = "") }
            }.onFailure { error ->
                if (generation == loadGeneration) _uiState.update {
                    it.copy(loading = false, error = error.message ?: "Unable to load F-Droid repository")
                }
            }
        }
    }

    fun install(app: FdroidApp) {
        val base = if (_uiState.value.source == "tuna") TUNA_REPO else OFFICIAL_REPO
        val url = "${base}${app.apkName}"
        downloads.start(
            DownloadMeta(
                appId = app.packageName.hashCode().toLong(),
                packageName = app.packageName,
                displayName = app.name,
                versionName = app.versionName,
                versionCode = app.versionCode,
                url = url,
                size = app.size,
                parts = listOf(DownloadPart("base", "base", url, app.size, app.sha256)),
                icon = app.iconUrl,
            ),
            installAfterDownload = true,
        )
    }

    private companion object {
        const val OFFICIAL_REPO = "https://f-droid.org/repo/"
        const val TUNA_REPO = "https://mirrors.tuna.tsinghua.edu.cn/fdroid/repo/"
    }
}

