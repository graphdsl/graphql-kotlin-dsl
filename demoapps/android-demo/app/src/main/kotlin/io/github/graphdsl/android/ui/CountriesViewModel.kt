package io.github.graphdsl.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.graphdsl.android.data.Country
import io.github.graphdsl.android.data.CountriesRepository
import io.github.graphdsl.android.graphql.query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class CountriesState {
    data object Loading : CountriesState()
    data class Success(val countries: List<Country>, val query: String) : CountriesState()
    data class Error(val message: String) : CountriesState()
}

class CountriesViewModel : ViewModel() {

    private val repository = CountriesRepository()

    private val _state = MutableStateFlow<CountriesState>(CountriesState.Loading)
    val state: StateFlow<CountriesState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = CountriesState.Loading
            runCatching { repository.fetchCountries() }
                .onSuccess { countries ->
                    _state.value = CountriesState.Success(countries, query {
                        countries {
                            code
                            name
                            capital
                            emoji
                            currency
                            continent {
                                name
                            }
                        }
                    })
                }
                .onFailure { e ->
                    _state.value = CountriesState.Error(e.message ?: "Unknown error")
                }
        }
    }
}
