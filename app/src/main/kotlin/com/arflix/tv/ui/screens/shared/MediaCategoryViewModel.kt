package com.arflix.tv.ui.screens.shared

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.IptvRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.mediaCategoryPreferences by preferencesDataStore(name = "media_category_preferences")

/**
 * Base ViewModel for Movies and Series category screens.
 * Handles category loading, filtering, favorites, and item aggregation.
 */
abstract class MediaCategoryViewModel(
    protected val mediaType: MediaType,
    protected val iptvRepository: IptvRepository,
    @ApplicationContext protected val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaCategoryUiState())
    val uiState: StateFlow<MediaCategoryUiState> = _uiState.asStateFlow()

    protected var categoriesMap: Map<String, List<MediaItem>> = emptyMap()
    
    private val favoritesKey = stringSetPreferencesKey("favorite_categories_${mediaType.name.lowercase()}")

    init {
        loadCategories()
    }

    protected abstract suspend fun fetchCategoriesAndItems(): Pair<List<String>, Map<String, List<MediaItem>>>

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Load favorites from DataStore (scoped by media type)
                val favorites = try {
                    context.mediaCategoryPreferences.data.first()[favoritesKey] ?: emptySet()
                } catch (e: Exception) {
                    emptySet()
                }

                val (categoryNames, newCategoriesMap) = fetchCategoriesAndItems()
                categoriesMap = newCategoriesMap

                // Sort categories (favorites first) to match UI display order
                val sortedCategories = categoryNames.sortedBy { 
                    if (favorites.contains(it)) 0 else 1
                }

                // Ensure selected category index is valid
                val safeIndex = _uiState.value.selectedCategoryIndex
                    .coerceIn(0, (sortedCategories.size - 1).coerceAtLeast(0))

                val selectedCategoryName = sortedCategories.getOrNull(safeIndex).orEmpty()
                val selectedItems = if (selectedCategoryName.isNotEmpty()) {
                    newCategoriesMap[selectedCategoryName] ?: emptyList()
                } else {
                    emptyList()
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    categories = categoryNames,
                    selectedCategoryIndex = safeIndex,
                    items = selectedItems,
                    favoriteCategories = favorites,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load ${mediaType.name.lowercase()} categories"
                )
            }
        }
    }

    fun selectCategory(categoryName: String) {
        val items = categoriesMap[categoryName] ?: emptyList()
        _uiState.value = _uiState.value.copy(items = items)
    }

    fun toggleFavoriteCategory(categoryName: String) {
        viewModelScope.launch {
            val current = _uiState.value.favoriteCategories.toMutableSet()
            if (current.contains(categoryName)) {
                current.remove(categoryName)
            } else {
                current.add(categoryName)
            }
            context.mediaCategoryPreferences.edit { prefs ->
                prefs[favoritesKey] = current
            }
            _uiState.value = _uiState.value.copy(favoriteCategories = current)
        }
    }

    fun setCategorySearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(categorySearchQuery = query)
    }

    fun setItemSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(itemSearchQuery = query)
    }

    fun refresh() {
        loadCategories()
    }
}
