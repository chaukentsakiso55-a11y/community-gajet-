package za.co.cyberpulse.communitygadget.network

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LanMeshManager(
    context: Context,
    private val onPayload: (sourceAddress: String, payload: ByteArray) -> Unit
) {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val sendExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var receiveSocket: DatagramSocket? = null
    private var receiveThread: Thread? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acquireWifiLock()
        receiveThread = Thread(::receiveLoop, "CommunityGadget-LAN-Receiver").apply {
            isDaemon = true
            start()
        }
    }

    fun broadcast(payload: ByteArray) {
        if (!running.get()) return
        val framedPayload = MAGIC + payload
        sendExecutor.execute {
            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.reuseAddress = true
                    broadcastAddresses().forEach { address ->
                        socket.send(DatagramPacket(framedPayload, framedPayload.size, address, PORT))
                    }
                }
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        receiveSocket?.close()
        receiveSocket = null
        receiveThread?.interrupt()
        receiveThread = null
        sendExecutor.shutdownNow()
        releaseWifiLock()
        MeshRuntime.setLanReady(false)
    }

    private fun receiveLoop() {
        try {
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(PORT))
            }
            receiveSocket = socket
            MeshRuntime.setLanReady(true)
            val buffer = ByteArray(MAX_PACKET_SIZE)
            while (running.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (error: SocketException) {
                    if (running.get()) MeshRuntime.setStatus("Wi-Fi LAN listener stopped")
                    break
                }
                if (!hasMagic(packet)) continue
                val start = packet.offset + MAGIC.size
                val end = packet.offset + packet.length
                val payload = packet.data.copyOfRange(start, end)
                val source = packet.address?.hostAddress ?: "unknown"
                onPayload("lan:$source", payload)
            }
        } catch (error: Exception) {
            if (running.get()) MeshRuntime.setStatus("Wi-Fi LAN unavailable")
        } finally {
            MeshRuntime.setLanReady(false)
            receiveSocket?.close()
            receiveSocket = null
        }
    }

    private fun hasMagic(packet: DatagramPacket): Boolean {
        if (packet.length <= MAGIC.size) return false
        for (index in MAGIC.indices) if (packet.data[packet.offset + index] != MAGIC[index]) return false
        return true
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { network -> network.isUp && !network.isLoopback }
                .forEach { network ->
                    network.interfaceAddresses.mapNotNull { it.broadcast }.forEach(targets::add)
                }
        }
        return targets
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "communitygadget:lan-listener"
        ).apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        wifiLock = null
    }

    private companion object {
        const val PORT = 45873
        const val MAX_PACKET_SIZE = 16 * 1024
        val MAGIC = byteArrayOf(0x43, 0x47, 0x4C, 0x32)
    }
}
