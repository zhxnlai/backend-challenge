package msr

import com.github.ajalt.clikt.output.TermUi.echo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import ru.gildor.coroutines.okhttp.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

fun crawl(
  initialUrls: List<String>,
  httpClient: OkHttpClient,
  maxDepth: Int
): Map<String, List<String>> = runBlocking {
  if (initialUrls.isEmpty()) {
    return@runBlocking emptyMap()
  }

  val eventLoop = Channel<Event>(UNLIMITED)
  val expected = AtomicInteger()
  val actual = AtomicInteger()
  val results = ConcurrentHashMap<String, List<String>>()

  for (url in initialUrls) {
    expected.incrementAndGet()
    eventLoop.send(SendRequest(url, maxDepth))
  }

  for (event in eventLoop) {
    when(event) {
      is SendRequest -> {
        val (url, remainingDepth) = event
        launch {
          val urlsInPage = try {
            val request = Request.Builder()
              .url(url)
              .build()
            val response = httpClient.newCall(request).await()
            val responseString = response.body!!.string()
            val doc = Jsoup.parse(responseString)
            val elements = doc.select("a")
            elements.map { it.attr("href") }.filter { it.toHttpUrlOrNull() != null }
          } catch (e: Throwable) {
            echo("Failed to load URL $url due to $e")
            emptyList()
          }
          for (urlInPage in urlsInPage) {
            if (results.contains(urlInPage) || remainingDepth < 1) {
              continue
            }
            expected.incrementAndGet()
            eventLoop.send(SendRequest(urlInPage, remainingDepth - 1))
          }
          eventLoop.send(HandleResponse(url, urlsInPage))
        }
      }
      is HandleResponse -> {
        val (url, urlsInPage) = event
        results[url] = urlsInPage
        actual.incrementAndGet()
        if (expected.get() == actual.get()) {
          break
        }
      }
    }
  }
  results
}

sealed class Event {
}
data class SendRequest(val url: String, val remainingDepth: Int): Event()
data class HandleResponse(val url: String, val urlsInPage: List<String>): Event()
