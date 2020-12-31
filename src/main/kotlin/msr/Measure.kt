package msr

import com.github.ajalt.clikt.output.TermUi.echo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import ru.gildor.coroutines.okhttp.await

/**
 * Loads a list of URLs in parallel.
 */
fun measure(
  urls: List<String>,
  httpClient: OkHttpClient,
  concurrency: Int
): Map<String, Measurement> = runBlocking {
  if (urls.isEmpty()) {
    return@runBlocking emptyMap()
  }

  val requestChannel = Channel<Pair<String, Request>>(concurrency)
  val responseChannel = Channel<Pair<String, Response?>>()

  launch {
    for (url in urls) {
      val request = Request.Builder()
        .url(url)
        .build()
      requestChannel.send(url to request)
    }
    requestChannel.close()
  }
  launch {
    for ((url, request) in requestChannel) {
      launch {
        val response = try {
          httpClient.newCall(request).await()
        } catch (e: Throwable) {
          echo("Failed to load URL $url due to $e")
          null
        }
        responseChannel.send(url to response)
      }
    }
  }
  val measurements = mutableMapOf<String, Measurement>()
  var responseCount = 0
  while (responseCount < urls.size) {
    val (url, response) = responseChannel.receive()
    responseCount += 1
    if (response == null) {
      continue
    }
    measurements[url] = toMeasurement(url, response)
  }
  measurements
}

private fun toMeasurement(url: String, response: Response): Measurement {
  val size = try {
    response.body!!.bytes().size.toLong()
  } catch (e: Throwable) {
    echo("Failed to read body size of $url due to $e, using content length instead")
    response.body!!.contentLength()
  }
  return Measurement(
    url = url,
    domain = response.request.url.host,
    bodySizeBytes = size,
    loadTimeMillis = response.receivedResponseAtMillis - response.sentRequestAtMillis
  )
}

data class Measurement(
  val url: String,
  val domain: String,
  val bodySizeBytes: Long,
  val loadTimeMillis: Long
)
