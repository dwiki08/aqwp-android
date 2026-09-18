package froztt13.python.aqw.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import froztt13.python.aqw.data.model.HubOverview
import froztt13.python.aqw.domain.coordinator.HubCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    hubCoordinator: HubCoordinator = HubCoordinator
) : ViewModel() {

    val hubOverview: StateFlow<HubOverview> = hubCoordinator.observeOverview()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = hubCoordinator.calculateOverview()
        )

    fun refreshStatus() {
        // Automatically kept up to date reactively via HubCoordinator flows
    }
}
