package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LineType {
    INPUT, OUTPUT, SYSTEM, SUCCESS, ERROR, WARNING, HEADER, NODE_INFO
}

data class TerminalLine(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val text: String,
    val type: LineType = LineType.OUTPUT,
    val timestamp: String = ""
)

enum class NodeStatus {
    LOCKED, BREACHING, HACKED, FIREWALL_ACTIVE, OVERLOADED
}

enum class NodeType {
    FIREWALL_GATEWAY,
    DATA_VAULT,
    CPU_CORE,
    PROXY_RELAY,
    ICE_BARRIER,
    SECURITY_MONITOR
}

data class SystemNode(
    val id: String,
    val name: String,
    val type: NodeType,
    val ipAddress: String,
    val securityLevel: Int, // 1 to 10
    var status: NodeStatus = NodeStatus.LOCKED,
    val dataContent: String = "ENCRYPTED_DATA_BLOCK",
    val memoryAddress: String = "0x7FFF"
)

enum class PuzzleStatus {
    IN_PROGRESS, SUCCESS, FAILED
}

data class PuzzleState(
    val isActive: Boolean = false,
    val targetNodeId: String = "",
    val targetNodeName: String = "",
    val securityLevel: Int = 1,
    val targetSequence: List<String> = emptyList(),
    val playerSequence: List<String> = emptyList(),
    val availableNodeTokens: List<String> = emptyList(),
    val timeRemainingSeconds: Int = 15,
    val maxTimeSeconds: Int = 15,
    val status: PuzzleStatus = PuzzleStatus.IN_PROGRESS,
    val errorMessage: String? = null
)

data class HackingUiState(
    val commandHistory: List<String> = emptyList(),
    val terminalLogs: List<TerminalLine> = emptyList(),
    val activeNodes: List<SystemNode> = emptyList(),
    val selectedNodeId: String? = null,
    val currentPrompt: String = "root@matrix-v3:~#",
    val activeInput: String = "",
    val securityAlertLevel: Int = 0, // 0 to 100%
    val ramAvailableMb: Int = 8192,
    val connectedTargetIp: String? = null,
    val isExecutingCommand: Boolean = false,
    val historyPointer: Int = -1,
    val puzzleState: PuzzleState = PuzzleState()
)

/**
 * ViewModel managing matrix terminal state, command execution history,
 * active matrix system nodes, and command syntax parsing for simulated hacking operations.
 * Also includes a visual sequence matching puzzle mini-game triggered during ICE breaches.
 */
class HackingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HackingUiState())
    val uiState: StateFlow<HackingUiState> = _uiState.asStateFlow()

    private var puzzleTimerJob: Job? = null

    private val hexPool = listOf(
        "0x7A", "0xE9", "0x1C", "0xFF",
        "0x3B", "0x82", "0x4D", "0xA1",
        "0x5E", "0x90", "0x2B", "0xC4"
    )

    init {
        initializeDefaultNodes()
        addSystemWelcomeLog()
    }

    private fun initializeDefaultNodes() {
        val initialNodes = listOf(
            SystemNode("NODE-01", "Gateway Proxy", NodeType.PROXY_RELAY, "192.168.1.1", 2, NodeStatus.LOCKED, "Proxy bypass tokens"),
            SystemNode("NODE-02", "Subnet Firewall", NodeType.FIREWALL_GATEWAY, "10.0.42.1", 4, NodeStatus.FIREWALL_ACTIVE, "ICE Firewall Configuration Rules"),
            SystemNode("NODE-03", "Mainframe Core", NodeType.CPU_CORE, "10.0.42.10", 7, NodeStatus.LOCKED, "Kernel Root Access Keys"),
            SystemNode("NODE-04", "Data Vault Alpha", NodeType.DATA_VAULT, "10.0.42.100", 6, NodeStatus.LOCKED, "CLASSIFIED: Cyberware Implant Schematics"),
            SystemNode("NODE-05", "Black ICE Sentinel", NodeType.ICE_BARRIER, "10.0.42.254", 9, NodeStatus.LOCKED, "Black ICE Countermeasure Payloads")
        )
        _uiState.update { it.copy(activeNodes = initialNodes) }
    }

    private fun addSystemWelcomeLog() {
        val logs = listOf(
            TerminalLine(text = "=== MATRIX TERMINAL v3.8.4 INITIALIZED ===", type = LineType.HEADER),
            TerminalLine(text = "System: Compact Interleaved GL Engine Connected", type = LineType.SYSTEM),
            TerminalLine(text = "Type 'help' or 'scan' to begin matrix node penetration.", type = LineType.SYSTEM),
            TerminalLine(text = "--------------------------------------------------", type = LineType.SYSTEM)
        )
        _uiState.update { it.copy(terminalLogs = logs) }
    }

    fun onInputChanged(newInput: String) {
        _uiState.update { it.copy(activeInput = newInput) }
    }

    fun executeCurrentCommand() {
        val input = _uiState.value.activeInput.trim()
        if (input.isEmpty()) return

        val newHistory = _uiState.value.commandHistory + input
        appendLog(TerminalLine(text = "${_uiState.value.currentPrompt} $input", type = LineType.INPUT))

        _uiState.update {
            it.copy(
                commandHistory = newHistory,
                activeInput = "",
                historyPointer = -1,
                isExecutingCommand = true
            )
        }

        parseAndExecuteCommand(input)
    }

    /**
     * Syntax parser for simulated hacking commands.
     * Supports tokenization into opcode and positional arguments.
     */
    private fun parseAndExecuteCommand(commandLine: String) {
        val tokens = commandLine.trim().split("\\s+".toRegex())
        if (tokens.isEmpty()) return

        val opCode = tokens[0].lowercase()
        val args = tokens.drop(1)

        viewModelScope.launch {
            delay(100) // Processing delay
            when (opCode) {
                "help", "man" -> handleHelpCommand()
                "scan", "netstat" -> handleScanCommand()
                "connect", "ssh" -> handleConnectCommand(args)
                "crack", "breach" -> handleCrackCommand(args)
                "nuke", "overload" -> handleOverloadCommand(args)
                "download", "cat" -> handleDownloadCommand(args)
                "clear", "cls" -> handleClearCommand()
                "status", "sysinfo" -> handleStatusCommand()
                "ping" -> handlePingCommand(args)
                "inject" -> handleInjectCommand(args)
                "disconnect", "exit" -> handleDisconnectCommand()
                else -> {
                    appendLog(TerminalLine(text = "ERR: Unknown command '$opCode'. Type 'help' for available syntax.", type = LineType.ERROR))
                }
            }
            _uiState.update { it.copy(isExecutingCommand = false) }
        }
    }

    private fun handleHelpCommand() {
        appendLog(TerminalLine(text = "=== MATRIX HACKING COMMAND PARSER ===", type = LineType.HEADER))
        appendLog(TerminalLine(text = "  scan              - Discover active system nodes in subnet", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "  connect <node_id> - Establish connection to node (e.g. connect NODE-01)", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "  crack <node_id>   - Initiate ICE breach / sequence matching puzzle minigame", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "  nuke <node_id>    - Overload node memory buffer to disable firewall", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "  download <node_id>- Decrypt and extract data payload from hacked node", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "  ping <node_id>    - Test network connectivity to target node", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "  inject <payload>  - Inject exploit string into connected target", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "  status            - Show system RAM, trace alert level, & connection info", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "  disconnect        - Terminate active node session", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "  clear             - Flush terminal screen buffer", type = LineType.OUTPUT))
    }

    private fun handleScanCommand() {
        appendLog(TerminalLine(text = "Scanning local subnet for active nodes...", type = LineType.SYSTEM))
        val nodes = _uiState.value.activeNodes
        nodes.forEach { node ->
            val statusColor = when (node.status) {
                NodeStatus.HACKED -> "[COMPROMISED]"
                NodeStatus.BREACHING -> "[BREACH IN PROGRESS]"
                NodeStatus.FIREWALL_ACTIVE -> "[FIREWALL]"
                NodeStatus.OVERLOADED -> "[OVERLOADED]"
                else -> "[LOCKED]"
            }
            appendLog(
                TerminalLine(
                    text = "  -> ${node.id} (${node.name}) | IP: ${node.ipAddress} | Sec: Lvl ${node.securityLevel} | $statusColor",
                    type = if (node.status == NodeStatus.HACKED) LineType.SUCCESS else LineType.NODE_INFO
                )
            )
        }
        appendLog(TerminalLine(text = "Scan complete. Found ${nodes.size} target nodes.", type = LineType.SYSTEM))
    }

    private fun handleConnectCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLog(TerminalLine(text = "ERR: Missing node target. Syntax: connect <node_id>", type = LineType.ERROR))
            return
        }
        val targetId = args[0].uppercase()
        val node = _uiState.value.activeNodes.find { it.id == targetId || it.name.lowercase().contains(targetId.lowercase()) }

        if (node == null) {
            appendLog(TerminalLine(text = "ERR: Target node '$targetId' not found in subnet.", type = LineType.ERROR))
            return
        }

        _uiState.update {
            it.copy(
                selectedNodeId = node.id,
                connectedTargetIp = node.ipAddress,
                currentPrompt = "root@${node.id.lowercase()}:~#"
            )
        }
        appendLog(TerminalLine(text = "Connected to ${node.name} (${node.ipAddress}). Node status: ${node.status}", type = LineType.SUCCESS))
    }

    private fun handleCrackCommand(args: List<String>) {
        val targetId = args.firstOrNull()?.uppercase() ?: _uiState.value.selectedNodeId
        if (targetId == null) {
            appendLog(TerminalLine(text = "ERR: Target node required. Syntax: crack <node_id>", type = LineType.ERROR))
            return
        }

        startHackingPuzzle(targetId)
    }

    /**
     * Starts the visual sequence matching puzzle mini-game for the target node.
     */
    fun startHackingPuzzle(nodeId: String) {
        val nodeIndex = _uiState.value.activeNodes.indexOfFirst { it.id.equals(nodeId, ignoreCase = true) }
        if (nodeIndex == -1) {
            appendLog(TerminalLine(text = "ERR: Node '$nodeId' not recognized.", type = LineType.ERROR))
            return
        }

        val node = _uiState.value.activeNodes[nodeIndex]
        if (node.status == NodeStatus.HACKED) {
            appendLog(TerminalLine(text = "Node ${node.id} is already compromised.", type = LineType.WARNING))
            return
        }

        // Generate target sequence based on node security level (3 to 6 items)
        val sequenceLength = (3 + node.securityLevel / 2).coerceAtMost(6)
        val targetSeq = hexPool.shuffled().take(sequenceLength)

        // Select distractor tokens to build grid pool (8 to 12 items)
        val distractors = hexPool.filter { it !in targetSeq }.shuffled().take(8 - sequenceLength)
        val availableTokens = (targetSeq + distractors).shuffled()

        // Time limit based on security level
        val maxTime = (22 - node.securityLevel * 2).coerceAtLeast(8)

        val updatedNodes = _uiState.value.activeNodes.toMutableList()
        updatedNodes[nodeIndex] = node.copy(status = NodeStatus.BREACHING)

        _uiState.update {
            it.copy(
                activeNodes = updatedNodes,
                selectedNodeId = node.id,
                puzzleState = PuzzleState(
                    isActive = true,
                    targetNodeId = node.id,
                    targetNodeName = node.name,
                    securityLevel = node.securityLevel,
                    targetSequence = targetSeq,
                    playerSequence = emptyList(),
                    availableNodeTokens = availableTokens,
                    timeRemainingSeconds = maxTime,
                    maxTimeSeconds = maxTime,
                    status = PuzzleStatus.IN_PROGRESS,
                    errorMessage = null
                )
            )
        }

        appendLog(TerminalLine(text = "=== VISUAL ICE BREACH PROTOCOL INITIATED ===", type = LineType.HEADER))
        appendLog(TerminalLine(text = "Target: ${node.id} (${node.name}) | Time Limit: ${maxTime}s", type = LineType.SYSTEM))
        appendLog(TerminalLine(text = "Match sequence: [ ${targetSeq.joinToString(" ")} ]", type = LineType.WARNING))

        // Start timer
        puzzleTimerJob?.cancel()
        puzzleTimerJob = viewModelScope.launch {
            while (_uiState.value.puzzleState.isActive && _uiState.value.puzzleState.status == PuzzleStatus.IN_PROGRESS) {
                delay(1000L)
                val currentPz = _uiState.value.puzzleState
                if (!currentPz.isActive || currentPz.status != PuzzleStatus.IN_PROGRESS) break

                val newTime = currentPz.timeRemainingSeconds - 1
                if (newTime <= 0) {
                    onPuzzleTimeExpired()
                    break
                } else {
                    _uiState.update { it.copy(puzzleState = it.puzzleState.copy(timeRemainingSeconds = newTime)) }
                }
            }
        }
    }

    /**
     * Handles user selection of a node token tile in the visual puzzle mini-game.
     */
    fun onPuzzleTokenSelected(token: String) {
        val pzState = _uiState.value.puzzleState
        if (!pzState.isActive || pzState.status != PuzzleStatus.IN_PROGRESS) return

        val newPlayerSeq = pzState.playerSequence + token
        val matchIndex = newPlayerSeq.lastIndex

        // Verify if the tapped token matches expected sequence at this position
        if (matchIndex < pzState.targetSequence.size && token == pzState.targetSequence[matchIndex]) {
            // Correct token match
            if (newPlayerSeq.size == pzState.targetSequence.size) {
                // COMPLETE MATCH - PUZZLE SUCCESS
                puzzleTimerJob?.cancel()
                val targetNodeId = pzState.targetNodeId
                val nodeIndex = _uiState.value.activeNodes.indexOfFirst { it.id == targetNodeId }

                val alertIncrease = (pzState.securityLevel * 5).coerceAtMost(30)
                val newAlert = (_uiState.value.securityAlertLevel + alertIncrease).coerceAtMost(100)

                val updatedNodes = _uiState.value.activeNodes.toMutableList()
                if (nodeIndex != -1) {
                    updatedNodes[nodeIndex] = updatedNodes[nodeIndex].copy(status = NodeStatus.HACKED)
                }

                _uiState.update {
                    it.copy(
                        activeNodes = updatedNodes,
                        securityAlertLevel = newAlert,
                        puzzleState = pzState.copy(
                            playerSequence = newPlayerSeq,
                            status = PuzzleStatus.SUCCESS,
                            errorMessage = "SEQUENCE VERIFIED! ACCESS GRANTED"
                        )
                    )
                }

                appendLog(TerminalLine(text = "SUCCESS: ICE sequence verified! Node $targetNodeId COMPROMISED.", type = LineType.SUCCESS))
                appendLog(TerminalLine(text = "ALERT: Trace detection level risen to $newAlert%", type = if (newAlert > 60) LineType.WARNING else LineType.SYSTEM))

                viewModelScope.launch {
                    delay(1600L)
                    _uiState.update { it.copy(puzzleState = it.puzzleState.copy(isActive = false)) }
                }
            } else {
                // Partial correct token
                _uiState.update {
                    it.copy(
                        puzzleState = pzState.copy(
                            playerSequence = newPlayerSeq,
                            errorMessage = null
                        )
                    )
                }
            }
        } else {
            // MISMATCH PENALTY
            val penalizedTime = (pzState.timeRemainingSeconds - 3).coerceAtLeast(0)
            if (penalizedTime <= 0) {
                onPuzzleTimeExpired()
            } else {
                _uiState.update {
                    it.copy(
                        puzzleState = pzState.copy(
                            playerSequence = emptyList(), // Reset player input
                            timeRemainingSeconds = penalizedTime,
                            errorMessage = "MISMATCH DETECTED! -3s PENALTY"
                        )
                    )
                }
                appendLog(TerminalLine(text = "ERR: Sequence mismatch! Time penalty -3s.", type = LineType.ERROR))
            }
        }
    }

    private fun onPuzzleTimeExpired() {
        puzzleTimerJob?.cancel()
        val pzState = _uiState.value.puzzleState
        val targetNodeId = pzState.targetNodeId
        val nodeIndex = _uiState.value.activeNodes.indexOfFirst { it.id == targetNodeId }

        val newAlert = (_uiState.value.securityAlertLevel + 15).coerceAtMost(100)

        val updatedNodes = _uiState.value.activeNodes.toMutableList()
        if (nodeIndex != -1 && updatedNodes[nodeIndex].status == NodeStatus.BREACHING) {
            updatedNodes[nodeIndex] = updatedNodes[nodeIndex].copy(status = NodeStatus.FIREWALL_ACTIVE)
        }

        _uiState.update {
            it.copy(
                activeNodes = updatedNodes,
                securityAlertLevel = newAlert,
                puzzleState = pzState.copy(
                    timeRemainingSeconds = 0,
                    status = PuzzleStatus.FAILED,
                    errorMessage = "SYSTEM LOCKOUT! TIMER EXPIRED"
                )
            )
        }

        appendLog(TerminalLine(text = "BREACH FAILED: Sequence timer expired on $targetNodeId! Trace alert +15%.", type = LineType.ERROR))

        viewModelScope.launch {
            delay(1800L)
            _uiState.update { it.copy(puzzleState = it.puzzleState.copy(isActive = false)) }
        }
    }

    fun abortPuzzle() {
        puzzleTimerJob?.cancel()
        val targetNodeId = _uiState.value.puzzleState.targetNodeId
        val nodeIndex = _uiState.value.activeNodes.indexOfFirst { it.id == targetNodeId }

        val updatedNodes = _uiState.value.activeNodes.toMutableList()
        if (nodeIndex != -1 && updatedNodes[nodeIndex].status == NodeStatus.BREACHING) {
            updatedNodes[nodeIndex] = updatedNodes[nodeIndex].copy(status = NodeStatus.LOCKED)
        }

        _uiState.update {
            it.copy(
                activeNodes = updatedNodes,
                puzzleState = PuzzleState(isActive = false)
            )
        }
        appendLog(TerminalLine(text = "ICE breach protocol aborted by user.", type = LineType.SYSTEM))
    }

    private fun handleOverloadCommand(args: List<String>) {
        val targetId = args.firstOrNull()?.uppercase() ?: _uiState.value.selectedNodeId
        if (targetId == null) {
            appendLog(TerminalLine(text = "ERR: Target node required. Syntax: nuke <node_id>", type = LineType.ERROR))
            return
        }

        val nodeIndex = _uiState.value.activeNodes.indexOfFirst { it.id == targetId }
        if (nodeIndex == -1) {
            appendLog(TerminalLine(text = "ERR: Node '$targetId' invalid.", type = LineType.ERROR))
            return
        }

        val node = _uiState.value.activeNodes[nodeIndex]
        val updatedNodes = _uiState.value.activeNodes.toMutableList()
        updatedNodes[nodeIndex] = node.copy(status = NodeStatus.OVERLOADED)

        _uiState.update { it.copy(activeNodes = updatedNodes) }
        appendLog(TerminalLine(text = "CRITICAL OVERLOAD: Buffer flood sent to ${node.id}. Node disabled.", type = LineType.WARNING))
    }

    private fun handleDownloadCommand(args: List<String>) {
        val targetId = args.firstOrNull()?.uppercase() ?: _uiState.value.selectedNodeId
        if (targetId == null) {
            appendLog(TerminalLine(text = "ERR: Specify node to extract data. Syntax: download <node_id>", type = LineType.ERROR))
            return
        }

        val node = _uiState.value.activeNodes.find { it.id == targetId }
        if (node == null) {
            appendLog(TerminalLine(text = "ERR: Node '$targetId' not found.", type = LineType.ERROR))
            return
        }

        if (node.status != NodeStatus.HACKED && node.status != NodeStatus.OVERLOADED) {
            appendLog(TerminalLine(text = "ERR: Access denied. Crack node security first.", type = LineType.ERROR))
            return
        }

        appendLog(TerminalLine(text = "Extracting data payload from memory address ${node.memoryAddress}...", type = LineType.SYSTEM))
        appendLog(TerminalLine(text = "PAYLOAD RECOVERED: [ ${node.dataContent} ]", type = LineType.SUCCESS))
    }

    private fun handlePingCommand(args: List<String>) {
        val targetId = args.firstOrNull()?.uppercase() ?: _uiState.value.selectedNodeId ?: "NODE-01"
        val node = _uiState.value.activeNodes.find { it.id == targetId }
        val ip = node?.ipAddress ?: "127.0.0.1"

        appendLog(TerminalLine(text = "PING $ip 56(84) bytes of data.", type = LineType.SYSTEM))
        appendLog(TerminalLine(text = "64 bytes from $ip: icmp_seq=1 ttl=64 time=${(8..24).random()}ms", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "64 bytes from $ip: icmp_seq=2 ttl=64 time=${(8..24).random()}ms", type = LineType.OUTPUT))
    }

    private fun handleInjectCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLog(TerminalLine(text = "ERR: Payload input missing. Syntax: inject <payload_string>", type = LineType.ERROR))
            return
        }
        val payload = args.joinToString(" ")
        appendLog(TerminalLine(text = "Injecting raw byte sequence: [ $payload ]", type = LineType.SYSTEM))
        appendLog(TerminalLine(text = "Payload executed successfully on target vector.", type = LineType.SUCCESS))
    }

    private fun handleStatusCommand() {
        val state = _uiState.value
        appendLog(TerminalLine(text = "=== SYSTEM METRICS & TRACE STATUS ===", type = LineType.HEADER))
        appendLog(TerminalLine(text = "RAM Pool: ${state.ramAvailableMb} MB / 16384 MB", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "Trace Detection Level: ${state.securityAlertLevel}%", type = if (state.securityAlertLevel > 50) LineType.WARNING else LineType.OUTPUT))
        appendLog(TerminalLine(text = "Connected IP: ${state.connectedTargetIp ?: "DISCONNECTED (LOCAL HOST)"}", type = LineType.OUTPUT))
        appendLog(TerminalLine(text = "Compromised Nodes: ${state.activeNodes.count { it.status == NodeStatus.HACKED }} / ${state.activeNodes.size}", type = LineType.OUTPUT))
    }

    private fun handleDisconnectCommand() {
        _uiState.update {
            it.copy(
                selectedNodeId = null,
                connectedTargetIp = null,
                currentPrompt = "root@matrix-v3:~#"
            )
        }
        appendLog(TerminalLine(text = "Session closed. Returned to local prompt.", type = LineType.SYSTEM))
    }

    private fun handleClearCommand() {
        _uiState.update { it.copy(terminalLogs = emptyList()) }
    }

    private fun appendLog(line: TerminalLine) {
        _uiState.update { it.copy(terminalLogs = it.terminalLogs + line) }
    }

    fun selectNode(nodeId: String) {
        _uiState.update { it.copy(selectedNodeId = nodeId) }
    }

    fun recallPreviousCommand() {
        val history = _uiState.value.commandHistory
        if (history.isEmpty()) return

        val currentPtr = _uiState.value.historyPointer
        val newPtr = if (currentPtr == -1) history.lastIndex else (currentPtr - 1).coerceAtLeast(0)

        _uiState.update {
            it.copy(
                historyPointer = newPtr,
                activeInput = history[newPtr]
            )
        }
    }
}

