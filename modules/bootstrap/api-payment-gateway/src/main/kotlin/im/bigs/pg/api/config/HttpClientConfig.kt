package im.bigs.pg.api.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class HttpClientConfig {

    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate =
        builder
//            .setConnectTimeout(Duration.ofSeconds(3))
//            .setReadTimeout(Duration.ofSeconds(5))
            .build()
}