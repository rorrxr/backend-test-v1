package im.bigs.pg.api.payment.dto

import com.fasterxml.jackson.annotation.JsonFormat
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

@Schema(description = "결제 생성 응답")
data class PaymentResponse(
    @Schema(description = "결제 ID", example = "99")
    val id: Long?,
    @Schema(description = "제휴사 ID", example = "1")
    val partnerId: Long,
    @Schema(description = "결제 금액", example = "10000")
    val amount: BigDecimal,
    @Schema(description = "적용된 수수료율", example = "0.0300")
    val appliedFeeRate: BigDecimal,
    @Schema(description = "수수료 금액", example = "400")
    val feeAmount: BigDecimal,
    @Schema(description = "정산금액 (amount - fee)", example = "9600")
    val netAmount: BigDecimal,
    @Schema(description = "카드 끝 4자리", example = "4242")
    val cardLast4: String?,
    @Schema(description = "승인번호", example = "PG20250101ABC123")
    val approvalCode: String,
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "승인시각 (UTC)", example = "2025-01-01T00:00:00Z")
    val approvedAt: LocalDateTime,
    @Schema(description = "상태", example = "APPROVED")
    val status: PaymentStatus,
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "생성시간 (UTC)", example = "2025-01-01T00:00:00Z")
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(p: Payment) = PaymentResponse(
            id = p.id,
            partnerId = p.partnerId,
            amount = p.amount,
            appliedFeeRate = p.appliedFeeRate,
            feeAmount = p.feeAmount,
            netAmount = p.netAmount,
            cardLast4 = p.cardLast4,
            approvalCode = p.approvalCode,
            approvedAt = p.approvedAt,
            status = p.status,
            createdAt = p.createdAt,
        )
    }
}

