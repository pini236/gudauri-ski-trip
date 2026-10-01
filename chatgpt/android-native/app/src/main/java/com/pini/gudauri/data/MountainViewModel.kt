package com.pini.gudauri.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: MountainData) : LoadState
    data class Failed(val message: String) : LoadState
}

class MountainViewModel(app: Application) : AndroidViewModel(app) {
    private val mutableState=MutableStateFlow<LoadState>(LoadState.Loading)
    val state=mutableState.asStateFlow()
    init { load() }
    fun load() { viewModelScope.launch {
        mutableState.value=LoadState.Loading
        mutableState.value=try {
            withContext(Dispatchers.IO) {
                fun read(name:String)=getApplication<Application>().assets.open("data/$name.json").bufferedReader().use { it.readText() }
                LoadState.Ready(DataParser.parse(read("runs-and-lifts"),read("terrain"),read("videos-seed"),read("trip")))
            }
        } catch (e:Exception) { LoadState.Failed(e.message ?: "לא ניתן לטעון את נתוני ההר") }
    } }
}
