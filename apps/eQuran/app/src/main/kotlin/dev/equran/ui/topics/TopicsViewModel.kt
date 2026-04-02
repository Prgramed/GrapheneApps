package dev.equran.ui.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equran.domain.model.Topic
import dev.equran.domain.repository.TopicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
) : ViewModel() {

    private val _allTopics = MutableStateFlow<List<Topic>>(emptyList())
    val filter = MutableStateFlow("")

    val topics: StateFlow<List<Topic>> = MutableStateFlow<List<Topic>>(emptyList()).also { state ->
        viewModelScope.launch {
            combine(_allTopics, filter) { all, q ->
                if (q.isBlank()) all
                else all.filter {
                    it.nameEn.contains(q, ignoreCase = true) ||
                        (it.nameAr?.contains(q, ignoreCase = true) == true) ||
                        (it.description?.contains(q, ignoreCase = true) == true) ||
                        it.category.contains(q, ignoreCase = true)
                }
            }.collect { state.value = it }
        }
    }

    init {
        viewModelScope.launch {
            _allTopics.value = topicRepository.getAll()
        }
    }
}
