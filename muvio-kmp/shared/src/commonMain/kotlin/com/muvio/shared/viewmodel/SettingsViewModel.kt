package com.muvio.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muvio.shared.domain.Addon
import com.muvio.shared.repository.AddonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val installedAddons: List<Addon> = emptyList(),
    val isAddonsLoading: Boolean = false,
    val installUrl: String = "",
    val isInstalling: Boolean = false,
    val toastMessage: String? = null,
)

class SettingsViewModel(private val addonRepo: AddonRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadAddons()
    }

    fun loadAddons() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddonsLoading = true)
            try {
                val addons = addonRepo.getInstalledAddons()
                _uiState.value = _uiState.value.copy(installedAddons = addons, isAddonsLoading = false)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isAddonsLoading = false)
            }
        }
    }

    fun onInstallUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(installUrl = url)
    }

    fun installAddon() {
        val url = _uiState.value.installUrl.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInstalling = true)
            try {
                addonRepo.installAddon(url)
                _uiState.value = _uiState.value.copy(isInstalling = false, installUrl = "", toastMessage = "Addon installed")
                loadAddons()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isInstalling = false, toastMessage = "Install failed: ${e.message}")
            }
        }
    }

    fun toggleAddon(addonId: String) {
        viewModelScope.launch {
            try {
                addonRepo.toggleAddon(addonId)
                loadAddons()
            } catch (_: Exception) {}
        }
    }

    fun removeAddon(addonId: String) {
        viewModelScope.launch {
            try {
                addonRepo.removeAddon(addonId)
                loadAddons()
                _uiState.value = _uiState.value.copy(toastMessage = "Addon removed")
            } catch (_: Exception) {}
        }
    }

    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
