package dev.emusic.playback.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpnpController @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val AVT_NS = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val RC_NS = "urn:schemas-upnp-org:service:RenderingControl:1"
        private val SOAP_XML = "text/xml; charset=utf-8".toMediaType()
    }

    suspend fun setUri(controlUrl: String, streamUrl: String, title: String = "", artist: String = "") =
        withContext(Dispatchers.IO) {
            val didl = buildDidlMetadata(streamUrl, title, artist)
            val escapedUrl = streamUrl.xmlEscape()
            val escapedDidl = didl.xmlEscape()

            val soap = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetAVTransportURI xmlns:u="$AVT_NS">
      <InstanceID>0</InstanceID>
      <CurrentURI>$escapedUrl</CurrentURI>
      <CurrentURIMetaData>$escapedDidl</CurrentURIMetaData>
    </u:SetAVTransportURI>
  </s:Body>
</s:Envelope>"""

            sendSoap(controlUrl, "SetAVTransportURI", soap)
        }

    suspend fun play(controlUrl: String) = withContext(Dispatchers.IO) {
        val soap = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Play xmlns:u="$AVT_NS">
      <InstanceID>0</InstanceID>
      <Speed>1</Speed>
    </u:Play>
  </s:Body>
</s:Envelope>"""
        sendSoap(controlUrl, "Play", soap)
    }

    suspend fun pause(controlUrl: String) = withContext(Dispatchers.IO) {
        val soap = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Pause xmlns:u="$AVT_NS">
      <InstanceID>0</InstanceID>
    </u:Pause>
  </s:Body>
</s:Envelope>"""
        sendSoap(controlUrl, "Pause", soap)
    }

    suspend fun stop(controlUrl: String) = withContext(Dispatchers.IO) {
        val soap = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Stop xmlns:u="$AVT_NS">
      <InstanceID>0</InstanceID>
    </u:Stop>
  </s:Body>
</s:Envelope>"""
        sendSoap(controlUrl, "Stop", soap)
    }

    suspend fun seek(controlUrl: String, positionMs: Long) = withContext(Dispatchers.IO) {
        val hours = positionMs / 3_600_000
        val minutes = (positionMs % 3_600_000) / 60_000
        val seconds = (positionMs % 60_000) / 1_000
        val target = "%d:%02d:%02d".format(hours, minutes, seconds)

        val soap = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Seek xmlns:u="$AVT_NS">
      <InstanceID>0</InstanceID>
      <Unit>REL_TIME</Unit>
      <Target>$target</Target>
    </u:Seek>
  </s:Body>
</s:Envelope>"""
        sendSoap(controlUrl, "Seek", soap)
    }

    suspend fun setVolume(renderingControlUrl: String, volumePercent: Int) = withContext(Dispatchers.IO) {
        val vol = volumePercent.coerceIn(0, 100)
        val soap = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetVolume xmlns:u="$RC_NS">
      <InstanceID>0</InstanceID>
      <Channel>Master</Channel>
      <DesiredVolume>$vol</DesiredVolume>
    </u:SetVolume>
  </s:Body>
</s:Envelope>"""
        sendSoapToUrl(renderingControlUrl, "$RC_NS#SetVolume", soap)
    }

    private fun sendSoapToUrl(url: String, soapAction: String, soapBody: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"$soapAction\"")
                .post(soapBody.toRequestBody(SOAP_XML))
                .build()
            val response = okHttpClient.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            Timber.w("UPnP SOAP error: ${e.message}")
            false
        }
    }

    private fun sendSoap(controlUrl: String, action: String, soapBody: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(controlUrl)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"$AVT_NS#$action\"")
                .post(soapBody.toRequestBody(SOAP_XML))
                .build()
            val response = okHttpClient.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            if (!success) Timber.w("UPnP $action failed: ${response.code}")
            success
        } catch (e: Exception) {
            Timber.e("UPnP $action error: ${e.message}")
            false
        }
    }

    private fun buildDidlMetadata(url: String, title: String, artist: String): String {
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
<item id="0" parentID="-1" restricted="1">
<dc:title>${title.xmlEscape()}</dc:title>
<dc:creator>${artist.xmlEscape()}</dc:creator>
<upnp:class>object.item.audioItem.musicTrack</upnp:class>
<res protocolInfo="http-get:*:audio/mpeg:*">${url.xmlEscape()}</res>
</item>
</DIDL-Lite>"""
    }

    suspend fun checkReachable(controlUrl: String): Boolean = withContext(Dispatchers.IO) {
        val soap = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:GetTransportInfo xmlns:u="$AVT_NS">
      <InstanceID>0</InstanceID>
    </u:GetTransportInfo>
  </s:Body>
</s:Envelope>"""
        sendSoap(controlUrl, "GetTransportInfo", soap)
    }

    private fun String.xmlEscape(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
