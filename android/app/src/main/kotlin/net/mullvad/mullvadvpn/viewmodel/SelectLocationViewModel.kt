package net.mullvad.mullvadvpn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.compose.state.RelayListState
import net.mullvad.mullvadvpn.compose.state.SelectLocationUiState
import net.mullvad.mullvadvpn.compose.state.toNullableOwnership
import net.mullvad.mullvadvpn.compose.state.toSelectedProviders
import net.mullvad.mullvadvpn.model.Constraint
import net.mullvad.mullvadvpn.model.Ownership
import net.mullvad.mullvadvpn.relaylist.Provider
import net.mullvad.mullvadvpn.relaylist.RelayItem
import net.mullvad.mullvadvpn.relaylist.filterOnSearchTerm
import net.mullvad.mullvadvpn.relaylist.toLocationConstraint
import net.mullvad.mullvadvpn.ui.serviceconnection.ServiceConnectionManager
import net.mullvad.mullvadvpn.ui.serviceconnection.connectionProxy
import net.mullvad.mullvadvpn.usecase.RelayListFilterUseCase
import net.mullvad.mullvadvpn.usecase.RelayListUseCase

class SelectLocationViewModel(
    private val serviceConnectionManager: ServiceConnectionManager,
    private val relayListUseCase: RelayListUseCase,
    private val relayListFilterUseCase: RelayListFilterUseCase
) : ViewModel() {
    private val _searchTerm = MutableStateFlow(EMPTY_SEARCH_TERM)

    val uiState =
        combine(
                relayListUseCase.relayListWithSelection(),
                _searchTerm,
                relayListFilterUseCase.selectedOwnership(),
                relayListFilterUseCase.availableProviders(),
                relayListFilterUseCase.selectedProviders()
            ) {
                (customList, relayCountries, selectedItem),
                searchTerm,
                selectedOwnership,
                allProviders,
                selectedConstraintProviders ->
                val selectedOwnershipItem = selectedOwnership.toNullableOwnership()
                val selectedProvidersCount =
                    when (selectedConstraintProviders) {
                        is Constraint.Any -> null
                        is Constraint.Only ->
                            filterSelectedProvidersByOwnership(
                                    selectedConstraintProviders.toSelectedProviders(allProviders),
                                    selectedOwnershipItem
                                )
                                .size
                    }

                val filteredRelayCountries =
                    relayCountries.filterOnSearchTerm(searchTerm, selectedItem)

                SelectLocationUiState.Data(
                    searchTerm = searchTerm,
                    selectedOwnership = selectedOwnershipItem,
                    selectedProvidersCount = selectedProvidersCount,
                    relayListState =
                        if (filteredRelayCountries.isNotEmpty()) {
                            RelayListState.RelayList(
                                countries = filteredRelayCountries,
                                selectedItem = selectedItem
                            )
                        } else {
                            RelayListState.Empty
                        },
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                SelectLocationUiState.Loading
            )

    private val _uiSideEffect = Channel<SelectLocationSideEffect>()
    val uiSideEffect = _uiSideEffect.receiveAsFlow()

    init {
        viewModelScope.launch { relayListUseCase.fetchRelayList() }
    }

    fun selectRelay(relayItem: RelayItem) {
        val locationConstraint = relayItem.toLocationConstraint()
        relayListUseCase.updateSelectedRelayLocation(locationConstraint)
        serviceConnectionManager.connectionProxy()?.connect()
        _uiSideEffect.trySend(SelectLocationSideEffect.CloseScreen)
    }

    fun onSearchTermInput(searchTerm: String) {
        viewModelScope.launch { _searchTerm.emit(searchTerm) }
    }

    private fun filterSelectedProvidersByOwnership(
        selectedProviders: List<Provider>,
        selectedOwnership: Ownership?
    ): List<Provider> =
        when (selectedOwnership) {
            Ownership.MullvadOwned -> selectedProviders.filter { it.mullvadOwned }
            Ownership.Rented -> selectedProviders.filterNot { it.mullvadOwned }
            else -> selectedProviders
        }

    fun removeOwnerFilter() {
        viewModelScope.launch {
            relayListFilterUseCase.updateOwnershipAndProviderFilter(
                Constraint.Any(),
                relayListFilterUseCase.selectedProviders().first()
            )
        }
    }

    fun removeProviderFilter() {
        viewModelScope.launch {
            relayListFilterUseCase.updateOwnershipAndProviderFilter(
                relayListFilterUseCase.selectedOwnership().first(),
                Constraint.Any()
            )
        }
    }

    companion object {
        private const val EMPTY_SEARCH_TERM = ""
    }
}

sealed interface SelectLocationSideEffect {
    data object CloseScreen : SelectLocationSideEffect
}
