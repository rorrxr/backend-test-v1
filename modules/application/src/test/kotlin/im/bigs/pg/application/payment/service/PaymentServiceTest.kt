package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.partner.Partner
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * PaymentService 단위 테스트
 *
 * 검증 포인트:
 * FeePolicy 기반 수수료 계산 (HALF_UP 반올림)
 * PartnerOutPort/FeePolicyOutPort 호출 및 fallback 정책 처리
 * Payment 저장 시 appliedFeeRate, feeAmount, netAmount 반영 확인
 * 전체 결제 생성(pay) 흐름 정상 동작 검증
 */
class 결제서비스Test {

    private val partnerRepo = mockk<PartnerOutPort>()
    private val feeRepo = mockk<FeePolicyOutPort>()
    private val paymentRepo = mockk<PaymentOutPort>()

    // Stub PG Client (실제 승인 구조 흉내)
    private val pgClient = object : PgClientOutPort {
        override fun supports(partnerId: Long) = true
        override fun approve(request: PgApproveRequest): PgApproveResult {
            return PgApproveResult(
                approvalCode = "TEST-APPROVED-001",
                approvedAt = LocalDateTime.of(2025, 1, 1, 0, 0),
                status = PaymentStatus.APPROVED
            )
        }
    }

    @Test
    @DisplayName("제휴사 FeePolicy 기반으로 반올림 포함 수수료를 계산하고 저장한다")
    fun `결제 시 FeePolicy를 기반으로 소수점 2자리 반올림 포함 수수료를 계산한다`() {
        // given
        val service = PaymentService(partnerRepo, feeRepo, paymentRepo, listOf(pgClient))

        every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "TestPartner", true)

        // FeePolicy: 2.35% + 99.999원 → 반올림 후 검증
        every { feeRepo.findEffectivePolicy(1L, any()) } returns FeePolicy(
            id = 10L,
            partnerId = 1L,
            effectiveFrom = LocalDateTime.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC),
            percentage = BigDecimal("0.0235"),
            fixedFee = BigDecimal("99.999")
        )

        val savedSlot = slot<Payment>()
        every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 100L) }

        val cmd = PaymentCommand(
            partnerId = 1L,
            amount = BigDecimal("12345"), // 테스트 금액
            cardBin = "123456",
            cardLast4 = "4242",
            productName = "테스트상품"
        )

        // when
        val result = service.pay(cmd)

        // then
        // (12345 * 0.0235) + 99.999 = 390.1065 → HALF_UP → 390.11
        assertEquals(100L, result.id)
        assertEquals(BigDecimal("0.0235"), result.appliedFeeRate)
        assertEquals(BigDecimal("390.11"), result.feeAmount)
        assertEquals(BigDecimal("11954.89"), result.netAmount)
        assertEquals(PaymentStatus.APPROVED, result.status)
        assertNotNull(result.approvalCode)

        // 저장 엔티티 검증
        val saved = savedSlot.captured
        assertEquals(BigDecimal("0.0235"), saved.appliedFeeRate)
        assertEquals(BigDecimal("390.11"), saved.feeAmount)
        assertEquals(BigDecimal("11954.89"), saved.netAmount)
    }

    @Test
    @DisplayName("FeePolicy가 없을 경우 기본 수수료(3% + 100원, HALF_UP 반올림)를 적용한다")
    fun `FeePolicy가 없을 경우 기본 수수료가 적용된다`() {
        // given
        val service = PaymentService(partnerRepo, feeRepo, paymentRepo, listOf(pgClient))

        every { partnerRepo.findById(2L) } returns Partner(2L, "BASIC", "기본제휴사", true)
        every { feeRepo.findEffectivePolicy(2L, any()) } returns null // 정책 없음

        val savedSlot = slot<Payment>()
        every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 200L) }

        val cmd = PaymentCommand(
            partnerId = 2L,
            amount = BigDecimal("10000"),
            cardLast4 = "1111"
        )

        // when
        val result = service.pay(cmd)

        // then
        // (10000 * 0.03) + 100 = 400.00
        assertEquals(200L, result.id)
        assertEquals(BigDecimal("0.0300"), result.appliedFeeRate)
        assertEquals(BigDecimal("400.00"), result.feeAmount)
        assertEquals(BigDecimal("9600.00"), result.netAmount)
        assertEquals(PaymentStatus.APPROVED, result.status)

        // 저장된 Payment에도 동일 값 반영
        val saved = savedSlot.captured
        assertEquals(BigDecimal("400.00"), saved.feeAmount)
        assertEquals(BigDecimal("9600.00"), saved.netAmount)
    }
}
