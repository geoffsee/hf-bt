package ltd.gsio.hfbt

import ltd.gsio.hfbt.config.DemoPeerProperties
import ltd.gsio.hfbt.config.WebseedProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(DemoPeerProperties::class, WebseedProperties::class)
class HfBtApplication

fun main(args: Array<String>) {
    runApplication<HfBtApplication>(*args)
}
