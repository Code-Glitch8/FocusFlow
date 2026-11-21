package com.example.nativebridge

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

data class LanPeerInfo(
    val hostAddress: String,
    val deviceName: String,
    val pairingCode: String,
    val tcpPort: Int,
    val seenAt: Long = System.currentTimeMillis()
)

class LanSyncManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var serverJob: Job? = null
    private var discoveryJob: Job? = null
    private var serverSocket: ServerSocket? = null

    fun startHostServer(
        pairingCodeProvider: () -> String,
        snapshotExporter: suspend () -> String,
        onIncomingSnapshot: suspend (peerName: String, json: String) -> Unit
    ) {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val socket = ServerSocket(TCP_PORT)
                serverSocket = socket
                while (isActive) {
                    val client = socket.accept()
                    launch {
                        handleIncomingClient(client, pairingCodeProvider(), snapshotExporter, onIncomingSnapshot)
                    }
                }
            }
        }

        discoveryJob = scope.launch(Dispatchers.IO) {
            runCatching {
                DatagramSocket(UDP_PORT).use { udp ->
                    udp.broadcast = true
                    val buf = ByteArray(512)
                    while (isActive) {
                        val packet = DatagramPacket(buf, buf.size)
                        udp.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = runCatching { JSONObject(msg) }.getOrNull() ?: continue
                        if (json.optString("type") == "DISCOVER" &&
                            json.optString("code").equals(pairingCodeProvider(), ignoreCase = true)
                        ) {
                            val reply = JSONObject().apply {
                                put("type", "HERE")
                                put("code", pairingCodeProvider())
                                put("device", android.os.Build.MODEL ?: "Android")
                                put("port", TCP_PORT)
                            }.toString().toByteArray(Charsets.UTF_8)
                            val replyPacket = DatagramPacket(reply, reply.size, packet.address, packet.port)
                            udp.send(replyPacket)
                        }
                    }
                }
            }
        }
    }

    suspend fun discoverPeers(pairingCode: String, timeoutMs: Long = 2000L): List<LanPeerInfo> =
        withContext(Dispatchers.IO) {
            val found = linkedMapOf<String, LanPeerInfo>()
            val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifiMgr?.createMulticastLock("focusflow_lan_sync")
            runCatching { lock?.acquire() }
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 500
                    val probe = JSONObject().apply {
                        put("type", "DISCOVER")
                        put("code", pairingCode.trim())
                    }.toString().toByteArray(Charsets.UTF_8)
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    socket.send(DatagramPacket(probe, probe.size, broadcastAddr, UDP_PORT))

                    val deadline = System.currentTimeMillis() + timeoutMs
                    val buf = ByteArray(1024)
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buf, buf.size)
                        val received = runCatching {
                            socket.receive(packet)
                            true
                        }.getOrDefault(false)
                        if (!received) continue
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val obj = runCatching { JSONObject(text) }.getOrNull() ?: continue
                        if (obj.optString("type") == "HERE") {
                            val host = packet.address.hostAddress ?: continue
                            found[host] = LanPeerInfo(
                                hostAddress = host,
                                deviceName = obj.optString("device", "FocusFlow Peer"),
                                pairingCode = obj.optString("code", pairingCode),
                                tcpPort = obj.optInt("port", TCP_PORT)
                            )
                        }
                    }
                }
            } finally {
                if (lock?.isHeld == true) {
                    runCatching { lock.release() }
                }
            }
            found.values.toList()
        }

    suspend fun syncWithPeer(
        hostAddress: String,
        port: Int,
        pairingCode: String,
        localSnapshotJson: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        Socket(hostAddress, port).use { socket ->
            socket.soTimeout = 6000
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            val payload = JSONObject().apply {
                put("code", pairingCode.trim())
                put("device", android.os.Build.MODEL ?: "Android")
                put("snapshot", JSONObject(localSnapshotJson))
            }.toString()
            writer.write(payload)
            writer.write("\n")
            writer.flush()

            val line = reader.readLine() ?: throw IllegalStateException("Peer closed connection without reply.")
            val reply = JSONObject(line)
            if (reply.has("error")) {
                throw IllegalStateException(reply.getString("error"))
            }
            val peerName = reply.optString("device", hostAddress)
            val peerSnapshot = reply.getJSONObject("snapshot").toString()
            peerName to peerSnapshot
        }
    }

    fun stop() {
        serverJob?.cancel()
        discoveryJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handleIncomingClient(
        client: Socket,
        expectedCode: String,
        snapshotExporter: suspend () -> String,
        onIncomingSnapshot: suspend (String, String) -> Unit
    ) {
        client.use { sock ->
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
            val line = reader.readLine() ?: return
            val req = runCatching { JSONObject(line) }.getOrNull() ?: return
            if (!req.optString("code").equals(expectedCode, ignoreCase = true)) {
                writer.write(JSONObject().put("error", "Pairing code mismatch.").toString() + "\n")
                writer.flush()
                return
            }
            val peerName = req.optString("device", sock.inetAddress.hostAddress ?: "Peer")
            val remoteSnapshot = req.optJSONObject("snapshot")?.toString()
            val localSnapshot = snapshotExporter()
            writer.write(
                JSONObject().apply {
                    put("device", android.os.Build.MODEL ?: "Android")
                    put("snapshot", JSONObject(localSnapshot))
                }.toString() + "\n"
            )
            writer.flush()
            if (remoteSnapshot != null) {
                onIncomingSnapshot(peerName, remoteSnapshot)
            }
        }
    }

    companion object {
        const val UDP_PORT = 48721
        const val TCP_PORT = 48722
    }
}
