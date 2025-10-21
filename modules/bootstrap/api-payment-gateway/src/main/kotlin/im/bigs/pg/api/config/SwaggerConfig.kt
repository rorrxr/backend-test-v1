package im.bigs.pg.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("NanoBanana Payments API")
                .description("결제 도메인 서버 OpenAPI 문서 (SpringDoc 기반)")
                .version("v1.0.0")
        )
}
