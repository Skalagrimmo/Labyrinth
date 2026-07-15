package com.example.ui.managers

import com.example.data.LogMessage
import com.example.data.LogType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages the game's message feed.
 * Responsible for adding, clearing, and retrieving log messages.
 * 
 * This manager handles all log-related state and operations,
 * keeping it isolated from the main GameViewModel.
 * 
 * Features:
 * - Prepends new messages to the front of the feed
 * - Automatically caps feed at 40 messages to prevent memory buildup
 * - Provides reactive StateFlow for Compose UI updates
 */
class LoggingManager {
    private val _logFeed = MutableStateFlow<List<LogMessage>>(emptyList())
    val logFeed: StateFlow<List<LogMessage>> = _logFeed.asStateFlow()

    /**
     * Adds a new log message to the feed.
     * 
     * @param message The text content of the log message
     * @param type The LogType (INFO, ALERT, SUCCESS, ERROR) for styling/filtering
     */
    fun addLog(message: String, type: LogType = LogType.INFO) {
        _logFeed.update { state ->
            val updatedFeed = state.toMutableList()
            updatedFeed.add(0, LogMessage(message, type)) // Prepend to see latest on top
            
            // Limit to 40 logs to prevent memory clog
            if (updatedFeed.size > 40) {
                updatedFeed.removeAt(updatedFeed.size - 1)
            }
            updatedFeed
        }
    }

    /**
     * Clears all log messages from the feed.
     * 
     * Useful for resetting logs when:
     * - Starting a new game
     * - Transitioning between major game states
     * - Debugging/testing
     */
    fun clearLogs() {
        _logFeed.value = emptyList()
    }

    /**
     * Retrieves the current snapshot of all log messages.
     * 
     * Note: For reactive updates, collect from the [logFeed] StateFlow instead.
     * This method is useful for non-reactive access or testing.
     * 
     * @return Immutable list of current log messages
     */
    fun getCurrentLogs(): List<LogMessage> = _logFeed.value

    /**
     * Gets the count of current log messages.
     * Useful for UI that wants to show "X messages" without collecting entire list.
     * 
     * @return Number of messages currently in the feed
     */
    fun getLogCount(): Int = _logFeed.value.size

    /**
     * Batch add multiple logs at once.
     * Useful for initialization or multi-step processes.
     * 
     * @param messages List of (text, type) pairs to add
     */
    fun addLogs(messages: List<Pair<String, LogType>>) {
        messages.forEach { (text, type) ->
            addLog(text, type)
        }
    }
}
