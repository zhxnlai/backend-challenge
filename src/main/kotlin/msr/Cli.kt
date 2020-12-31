package msr

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.jakewharton.fliptables.FlipTable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.nio.file.Paths
import kotlin.system.exitProcess

const val VERSION = "0.1.0"
const val NUM_CONCURRENT_REQUESTS = 100

class Msr : CliktCommand() {

  override fun run() {
  }
}

class Version : CliktCommand() {

  override fun run() {
    echo(VERSION)
    exitProcess(0)
  }
}

class Register(
  private val registry: Registry
) : CliktCommand() {
  private val rawUrls by argument().multiple(required = true)

  override fun run() {
    validate()
    registry.saveUrls(rawUrls)
    exitProcess(0)
  }

  private fun validate() {
    for (rawUrl in rawUrls) {
      rawUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("Malformed URL: $rawUrl")
    }
  }
}

class Measure(
  private val registry: Registry,
  private val httpClient: OkHttpClient,
) : CliktCommand() {

  override fun run() {
    val urls = registry.loadAllUrls()
    val measurements = measure(urls, httpClient, NUM_CONCURRENT_REQUESTS)
    printPageSizes(urls, measurements)
    exitProcess(0)
  }

  private fun printPageSizes(urls: List<String>, measurements: Map<String, Measurement>) {
    val tableHeaders = arrayOf("URL", "Size (Bytes)")
    val tableRows = mutableListOf<Array<String>>()
    for (url in urls) {
      val measurement = measurements[url]
      if (measurement == null) {
        tableRows.add(arrayOf(url, "N/A"))
        continue
      }
      tableRows.add(arrayOf(url, measurement.bodySizeBytes.toString()))
    }
    echo(FlipTable.of(tableHeaders, tableRows.toTypedArray()))
  }
}

class Race(
  private val registry: Registry,
  private val httpClient: OkHttpClient,
) : CliktCommand() {

  override fun run() {
    val urls = registry.loadAllUrls()
    val measurements = measure(urls, httpClient, NUM_CONCURRENT_REQUESTS)
    printLoadTime(measurements.values.toList())
    exitProcess(0)
  }

  private fun printLoadTime(measurements: List<Measurement>) {
    val tableHeaders = arrayOf("Domain", "Average Load Time (Millis)")
    val tableRows = mutableListOf<Array<String>>()
    for ((domain, measurementsByDomain) in measurements.groupBy { it.domain }) {
      val averageLoadTimeMillis = measurementsByDomain
        .map { it.loadTimeMillis }
        .average()
      tableRows.add(arrayOf(domain, averageLoadTimeMillis.toString()))
    }
    echo(FlipTable.of(tableHeaders, tableRows.toTypedArray()))
  }
}

fun main(args: Array<String>) {
  val httpClient = OkHttpClient()
  val configPath = Paths.get("${System.getProperty("user.home")}/.msr/db")
  val registry = Registry(configPath)
  Msr()
    .subcommands(
      Version(),
      Register(registry),
      Measure(registry, httpClient),
      Race(registry, httpClient)
    )
    .main(args)
}
