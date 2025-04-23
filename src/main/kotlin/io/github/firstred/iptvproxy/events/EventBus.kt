package io.github.firstred.iptvproxy.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class EventBus {
    val flow = MutableSharedFlow<Event>(1_000)

    fun dispatch(event: Event) = coroutineScope.launch {
         flow.emit(event)
    }

    companion object {
        val coroutineScope = CoroutineScope(Job())
    }
}
