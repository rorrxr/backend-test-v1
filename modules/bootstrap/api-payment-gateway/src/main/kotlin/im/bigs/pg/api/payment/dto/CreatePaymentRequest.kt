package im.bigs.pg.api.payment.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import java.math.BigDecimal

@Schema(description = "결제 생성 요청")
data class CreatePaymentRequest(
    @Schema(description = "제휴사 ID", example = "1")
    val partnerId: Long,

    @field:Min(1)
    @Schema(description = "결제 금액", example = "10000")
    val amount: BigDecimal,

    @Schema(description = "카드 BIN (앞 6자리)", example = "123456")
    val cardBin: String? = null,

    @Schema(description = "카드 번호 끝 4자리", example = "4242")
    val cardLast4: String? = null,

    @Schema(description = "상품명", example = "나노바나나 무선충전패드")
    val productName: String? = null,
)

