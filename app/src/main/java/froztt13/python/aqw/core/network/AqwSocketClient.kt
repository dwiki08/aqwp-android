package froztt13.python.aqw.core.network

import android.util.Log
import froztt13.python.aqw.data.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

open class AqwSocketClient {

    var tag: String = ""

    companion object {
        private const val TAG = "AqwSocketClient"
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val MAX_PACKET_LOGS = 500

        private val _packetLogs = MutableStateFlow<List<LogEntry>>(emptyList())
        val packetLogs: StateFlow<List<LogEntry>> = _packetLogs.asStateFlow()

        fun logPacketSent(tag: String, packet: String) {
            val entry = LogEntry(
                botType = "Packet",
                username = tag,
                message = packet
            )
            _packetLogs.update { current ->
                (current + entry).takeLast(MAX_PACKET_LOGS)
            }
        }

        fun clearPacketLogs() {
            _packetLogs.value = emptyList()
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val writeMutex = Mutex()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<String> = _incomingPackets.asSharedFlow()

    suspend fun connect(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        try {
            val sock = Socket()
            sock.tcpNoDelay = true
            sock.soTimeout = 0 // non-blocking or managed by read loop
            sock.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)

            socket = sock
            inputStream = sock.getInputStream()
            outputStream = sock.getOutputStream()
            _isConnected.value = true

            startReadLoop()
            Log.d(TAG, "Connected to $ip:$port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $ip:$port: ${e.message}", e)
            disconnect()
            false
        }
    }

    private fun startReadLoop() {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val stream = inputStream ?: return@launch
            val buffer = ByteArrayOutputStream()
            val readBuf = ByteArray(2048)

            try {
                while (isActive && _isConnected.value) {
                    val bytesRead = stream.read(readBuf)
                    if (bytesRead == -1) {
                        Log.d(TAG, "Server closed socket stream (EOF)")
                        break
                    }

                    for (i in 0 until bytesRead) {
                        val b = readBuf[i]
                        if (b == 0.toByte()) {
                            val packet = buffer.toString("UTF-8")
                            buffer.reset()
                            if (packet.isNotEmpty()) {
                                _incomingPackets.emit(packet)
                            }
                        } else {
                            buffer.write(b.toInt())
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive && _isConnected.value) {
                    Log.w(TAG, "Socket read exception: ${e.message}")
                }
            } finally {
                disconnect()
            }
        }
    }

    open suspend fun send(packet: String): Boolean = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext false
        writeMutex.withLock {
            try {
//                Log.d(TAG, "send: $packet")
                val stream = outputStream ?: return@withLock false
                val bytes = (packet + "\u0000").toByteArray(Charsets.UTF_8)
                stream.write(bytes)
                stream.flush()
                logPacketSent(tag, packet)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing packet: ${e.message}")
                disconnect()
                false
            }
        }
    }

    fun disconnect() {
        _isConnected.value = false
        readJob?.cancel()
        readJob = null

        try {
            inputStream?.close()
        } catch (_: Exception) {
        }
        try {
            outputStream?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }

        inputStream = null
        outputStream = null
        socket = null
        Log.d(TAG, "Socket disconnected")
    }
}
