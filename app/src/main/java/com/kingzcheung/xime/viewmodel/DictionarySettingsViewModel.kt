package com.kingzcheung.xime.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.DictEntry
import com.kingzcheung.xime.settings.DictionaryHelper
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SchemaMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DictionaryUiState(
    val availableSchemas: List<SchemaMeta> = emptyList(),
    val selectedSchema: String = "",
    val searchQuery: String = "",
    val allEntries: List<DictEntry> = emptyList(),
    val displayedEntries: List<DictEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isOperating: Boolean = false,
    val operationMessage: String = "",
    val toastMessage: String? = null
)

class DictionarySettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    init {
        loadSchemas()
    }

    private fun loadSchemas() {
        viewModelScope.launch {
            val (schemas, enabledIds) = withContext(Dispatchers.IO) {
                val allSchemas = SchemaManager.discoverSchemas(context)
                val enabled = SchemaManager.getEnabledSchemas(context)
                Pair(allSchemas, enabled)
            }
            val currentSchema = enabledIds.firstOrNull()
                ?: schemas.firstOrNull()?.schemaId
                ?: ""

            _uiState.update { it.copy(
                availableSchemas = schemas,
                selectedSchema = currentSchema
            )}

            if (currentSchema.isNotEmpty()) {
                loadDictionary(currentSchema)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectSchema(schemaId: String) {
        if (schemaId == _uiState.value.selectedSchema) return
        _uiState.update { it.copy(
            selectedSchema = schemaId,
            isLoading = true,
            searchQuery = "",
            allEntries = emptyList(),
            displayedEntries = emptyList()
        )}
        loadDictionary(schemaId)
    }

    private fun loadDictionary(schemaId: String) {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                DictionaryHelper.loadDictionary(context, schemaId)
            }

            _uiState.update { it.copy(
                allEntries = entries,
                displayedEntries = entries.take(50),
                isLoading = false
            )}
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        val allEntries = _uiState.value.allEntries
        val displayed = if (query.isEmpty()) {
            allEntries.take(50)
        } else {
            DictionaryHelper.searchDictionary(allEntries, query)
        }

        _uiState.update { it.copy(displayedEntries = displayed) }
    }

    fun clearSearch() {
        setSearchQuery("")
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    /**
     * 导入外部词库并自动触发部署编译
     */
    fun importDictionary(uri: Uri) {
        if (_uiState.value.isOperating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperating = true, operationMessage = "正在读取并校验词库文件...") }
            val importResult = DictionaryHelper.importDictionary(context, uri)
            if (importResult.isFailure) {
                _uiState.update { it.copy(
                    isOperating = false,
                    operationMessage = "",
                    toastMessage = importResult.exceptionOrNull()?.message ?: "导入失败"
                )}
                return@launch
            }

            _uiState.update { it.copy(operationMessage = "导入成功，正在重新部署并编译索引...") }
            val deploySuccess = withContext(Dispatchers.IO) {
                val engine = RimeEngine.getInstance()
                val ok = engine.deploy()
                if (ok) {
                    RimeConfigHelper.storeDeploymentHash(context)
                }
                ok
            }

            _uiState.update { it.copy(
                isOperating = false,
                operationMessage = "",
                toastMessage = if (deploySuccess) "词库导入并部署成功！" else "词库已导入，但部署失败，请稍后重试"
            )}

            val current = _uiState.value.selectedSchema
            if (current.isNotEmpty()) {
                loadDictionary(current)
            }
        }
    }

    /**
     * 从雾凇官方源同步更新 6 份核心词库并重新部署
     */
    fun updateFrostDictionaries() {
        if (_uiState.value.isOperating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOperating = true, operationMessage = "准备下载词库更新...") }
            val updateResult = DictionaryHelper.updateFrostDicts(context) { current, total, fileName ->
                _uiState.update { it.copy(operationMessage = "正在下载 ($current/$total): $fileName ...") }
            }

            if (updateResult.isFailure) {
                _uiState.update { it.copy(
                    isOperating = false,
                    operationMessage = "",
                    toastMessage = updateResult.exceptionOrNull()?.message ?: "更新下载失败"
                )}
                return@launch
            }

            _uiState.update { it.copy(operationMessage = "下载完成，正在重新部署词库并编译双数组索引...") }
            val deploySuccess = withContext(Dispatchers.IO) {
                val engine = RimeEngine.getInstance()
                val ok = engine.deploy()
                if (ok) {
                    RimeConfigHelper.storeDeploymentHash(context)
                }
                ok
            }

            _uiState.update { it.copy(
                isOperating = false,
                operationMessage = "",
                toastMessage = if (deploySuccess) "雾凇词库已全部更新并部署成功！" else "词库已下载，但部署编译失败"
            )}

            val current = _uiState.value.selectedSchema
            if (current.isNotEmpty()) {
                loadDictionary(current)
            }
        }
    }
}
