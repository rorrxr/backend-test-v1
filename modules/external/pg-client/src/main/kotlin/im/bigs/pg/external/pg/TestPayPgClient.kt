package im.bigs.pg.external.pg

import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.partner.PgType
import im.bigs.pg.domain.payment.PaymentStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.ZoneOffset

data class TestPayResponse(
    val approvalCode: String?,
    val success: Boolean
)

@Component
class TestPayPgClient(
    private val restTemplate: RestTemplate,
    @Value("\${pg.testpay.url}") private val baseUrl: String
) : PgClientOutPort {
    override val pgType = PgType.TESTOUTPAY

    override fun approve(request: PgApproveRequest): PgApproveResult {
        val body = mapOf(
            "amount" to request.amount,
            "cardBin" to request.cardBin,
            "cardLast4" to request.cardLast4,
            "productName" to request.productName
        )
        val resp = restTemplate.postForEntity("$baseUrl/payments", body, TestPayResponse::class.java)
        return PgApproveResult(
            approvalCode = resp.body?.approvalCode ?: "TP-UNKNOWN",
            approvedAt = LocalDateTime.now(ZoneOffset.UTC),
            status = if (resp.body?.success == true)
                PaymentStatus.APPROVED else PaymentStatus.CANCELED
        )
    }
}
