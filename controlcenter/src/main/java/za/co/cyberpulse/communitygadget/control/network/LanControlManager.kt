package za.co.cyberpulse.communitygadget.control.network

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

class LanControlManager(
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
        receiveThread = Thread(::receiveLoop, "CommunityControl-LAN").apply {
            isDaemon = true
            start()
        }
        ControlRuntime.setLanReady(true)
    }

    fun broadcast(payload: ByteArray) {
        if (!running.get()) return
        val framed = MAGIC + payload
        sendExecutor.execute {
            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.reuseAddress = true
                    broadcastAddresses().forEach { address ->
                        socket.send(DatagramPacket(framed, framed.size, address, PORT))
                    }
                }
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        receiveSocket?.close()
        receiveThread?.interrupt()
        sendExecutor.shutdownNow()
        releaseWifiLock()
        ControlRuntime.setLanReady(false)
    }

    private fun receiveLoop() {
        try {
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(PORT))
            }
            receiveSocket = socket
            val buffer = ByteArray(MAX_PACKET_SIZE)
            while (running.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (error: SocketException) {
                    if (running.get()) ControlRuntime.setStatus("LAN listener stopped")
                    break
                }
                if (!hasMagic(packet)) continue
                val start = packet.offset + MAGIC.size
                val end = packet.offset + packet.length
                onPayload(packet.address?.hostAddress ?: "unknown", packet.data.copyOfRange(start, end))
            }
        } catch (_: Exception) {
            if (running.get()) {
                ControlRuntime.setLanReady(false)
                ControlRuntime.setStatus("Wi-Fi LAN unavailable")
            }
        } finally {
            receiveSocket?.close()
            receiveSocket = null
        }
    }

    private fun hasMagic(packet: DatagramPacket): Boolean {
        if (packet.length <= MAGIC.size) return false
        for (i in MAGIC.indices) if (packet.data[packet.offset + i] != MAGIC[i]) return false
        return true
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .forEach { network -> network.interfaceAddresses.mapNotNull { it.broadcast }.forEach(targets::add) }
        }
        return targets
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        val manager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        wifiLock = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "communitycontrol:lan").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let { if (it.isHeld) runCatching { it.release() } }
        wifiLock = null
    }

    private companion object {
        const val PORT = 45873
        const val MAX_PACKET_SIZE = 16 * 1024
        val MAGIC = byteArrayOf(0x43, 0x47, 0x4C, 0x31)
    }
}
