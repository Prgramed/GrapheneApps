package dev.emusic.playback.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

data class CastDevice(
    val name: String,
    val location: String,
    val controlUrl: String,
    val renderingControlUrl: String? = null,
    val udn: String,
)

@Singleton
class DeviceDiscovery @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SEARCH_TARGET = "urn:schemas-upnp-org:service:AVTransport:1"
        private val LOCATION_PATTERN = Pattern.compile("LOCATION:\\s*(.+)", Pattern.CASE_INSENSITIVE)
    }

    suspend fun discover(timeoutMs: Long = 3000): List<CastDevice> = withContext(Dispatchers.IO) {
        val locationUrls = mutableSetOf<String>()

        try {
            val searchMessage = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n")
                append("\r\n")
            }

            val group = InetAddress.getByName(SSDP_ADDRESS)
            val socket = java.net.MulticastSocket(null).apply {
                reuseAddress = true
                soTimeout = timeoutMs.toInt()
                joinGroup(group)
            }

            val sendData = searchMessage.toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size, group, SSDP_PORT)
            socket.send(sendPacket)

            // Collect SSDP response URLs first (fast)
            withTimeoutOrNull(timeoutMs) {
                val buffer = ByteArray(4096)
                while (true) {
                    try {
                        val receivePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        val matcher = LOCATION_PATTERN.matcher(response)
                        if (matcher.find()) {
                            matcher.group(1)?.trim()?.let { locationUrls.add(it) }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                }
            }
            socket.close()
        } catch (e: Exception) {
            Timber.e("SSDP discovery failed: ${e.message}")
        }

        // Fetch device descriptions (fast — typically 1-3 devices)
        val devices = locationUrls.mapNotNull { location ->
            try {
                fetchDeviceDescription(location)
            } catch (e: Exception) {
                Timber.w("Failed to fetch device at $location: ${e.message}")
                null
            }
        }

        Timber.d("SSDP found ${devices.size} devices: ${devices.map { it.name }}")
        devices
    }

    private fun fetchDeviceDescription(location: String): CastDevice? {
        val request = Request.Builder().url(location).build()
        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        response.close()

        val name = extractXmlValue(body, "friendlyName") ?: return null
        val udn = extractXmlValue(body, "UDN") ?: location

        // Find AVTransport service controlURL
        val controlUrl = extractServiceControlUrl(body, location, "AVTransport") ?: return null
        val renderingControlUrl = extractServiceControlUrl(body, location, "RenderingControl")

        return CastDevice(
            name = name,
            location = location,
            controlUrl = controlUrl,
            renderingControlUrl = renderingControlUrl,
            udn = udn,
        )
    }

    private fun extractServiceControlUrl(xml: String, baseLocation: String, serviceName: String): String? {
        val pattern = Pattern.compile(
            "$serviceName.*?<controlURL>([^<]+)</controlURL>",
            Pattern.DOTALL,
        )
        val matcher = pattern.matcher(xml)
        if (!matcher.find()) return null

        val controlPath = matcher.group(1) ?: return null

        // Resolve relative URL
        return if (controlPath.startsWith("http")) {
            controlPath
        } else {
            val baseUrl = baseLocation.substringBeforeLast("/")
            if (controlPath.startsWith("/")) {
                val uri = java.net.URI(baseLocation)
                "${uri.scheme}://${uri.host}:${uri.port}$controlPath"
            } else {
                "$baseUrl/$controlPath"
            }
        }
    }

    private fun extractXmlValue(xml: String, tag: String): String? {
        val pattern = Pattern.compile("<$tag>([^<]+)</$tag>")
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }
}
