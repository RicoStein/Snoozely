package com.tigonic.snoozely

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            TimerPreferenceHelper.getTimerRunning(application).first()
            TimerPreferenceHelper.getTimerStartTime(application).first()
            TimerPreferenceHelper.getTimer(application).first()
            _isLoading.value = false
        }
    }
}
