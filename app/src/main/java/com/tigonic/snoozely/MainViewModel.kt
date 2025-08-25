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
        // Starte den Ladevorgang sofort bei der Initialisierung des ViewModels
        viewModelScope.launch {
            // Lade die kritischen Werte aus dem DataStore, auf die die UI wartet.
            // .first() sorgt dafür, dass wir hier warten, bis der erste Wert da ist.
            TimerPreferenceHelper.getTimerRunning(application).first()
            TimerPreferenceHelper.getTimerStartTime(application).first()
            TimerPreferenceHelper.getTimer(application).first()

            // Wenn alle Daten geladen sind, setze isLoading auf false.
            // Der Splash-Screen wird dann ausgeblendet.
            _isLoading.value = false
        }
    }
}
