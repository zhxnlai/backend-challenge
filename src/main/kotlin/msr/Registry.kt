package msr

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.sink
import okio.source
import java.nio.file.Files
import java.nio.file.Path

/**
 * This Registry maintains a list of URLs. It stores them as a JSON string in the file system
 * at [configPath].
 */
class Registry(
  private val configPath: Path,
) {

  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()
  private val adapter = moshi.adapter(Config::class.java)

  init {
    val parentDir = configPath.parent
    if (!Files.exists(parentDir)) {
      Files.createDirectories(parentDir)
    }
  }

  @Synchronized
  fun saveUrls(urls: List<String>) {
    val existingConfig = loadConfig()
    val newUrls = mutableSetOf<String>()
    newUrls.addAll(existingConfig.urls.keys)
    newUrls.addAll(urls)
    val newConfig = existingConfig.copy(
      urls = newUrls.associateWith { true }
    )
    saveConfig(newConfig)
  }

  @Synchronized
  fun loadAllUrls(): List<String> {
    return loadConfig().urls.keys.toList()
  }

  private fun saveConfig(config: Config) {
    configPath.toFile().sink().buffer().use { buffer ->
      buffer.write(adapter.toJson(config).encodeUtf8())
    }
  }

  private fun loadConfig(): Config {
    if (!Files.exists(configPath)) {
      return Config()
    }
    return adapter.fromJson(configPath.toFile().source().buffer()) ?: Config()
  }
}

data class Config(
  val urls: Map<String, Boolean> = emptyMap()
)
