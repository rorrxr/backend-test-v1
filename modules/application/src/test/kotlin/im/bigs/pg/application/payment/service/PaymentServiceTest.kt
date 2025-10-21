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
 * 검증 포인트
 * FeeCalculator가 FeePolicy 기반으로 수수료 계산하는지 검증
 * PartnerPersistenceAdapter를 통해 FeePolicy 조회가 이루어지는지 검증
 * 계산 결과(appliedFeeRate, feeAmount, netAmount)가 Payment에 저장되는지 검증
 * MockPgClient 대신 실제 TestPgClient 호출 구조를 흉내내는 approve 로직 검증
 * 전체 결제 생성(pay) 흐름이 정상적으로 완료되는지 검증
 */
class 결제서비스Test {

    private val partnerRepo = mockk<PartnerOutPort>()
    private val feeRepo = mockk<FeePolicyOutPort>()
    private val paymentRepo = mockk<PaymentOutPort>()

    // 실제 TestPgClient 연동 구조를 흉내내는 Stub
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
    @DisplayName("제휴사 FeePolicy 기반으로 수수료를 계산하고 저장한다")
    fun `결제 시 FeePolicy를 기반으로 수수료를 계산하고 저장해야 한다`() {
        // given
        val service = PaymentService(partnerRepo, feeRepo, paymentRepo, listOf(pgClient))

        every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "TestPartner", true)

        // FeePolicy 기반 (5% + 200원)
        every { feeRepo.findEffectivePolicy(1L, any()) } returns FeePolicy(
            id = 10L,
            partnerId = 1L,
            effectiveFrom = LocalDateTime.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC),
            percentage = BigDecimal("0.05"),
            fixedFee = BigDecimal("200")
        )

        val savedSlot = slot<Payment>()
        every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 100L) }

        val cmd = PaymentCommand(
            partnerId = 1L,
            amount = BigDecimal("10000"),
            cardBin = "123456",
            cardLast4 = "4242",
            productName = "테스트상품"
        )

        // when
        val result = service.pay(cmd)

        // then
        assertEquals(100L, result.id)
        assertEquals(BigDecimal("0.05"), result.appliedFeeRate)
        assertEquals(BigDecimal("700"), result.feeAmount)   // (10000 * 0.05) + 200
        assertEquals(BigDecimal("9300"), result.netAmount)  // 10000 - 700
        assertEquals(PaymentStatus.APPROVED, result.status)
        assertNotNull(result.approvalCode)

        val saved = savedSlot.captured
        assertEquals(BigDecimal("0.05"), saved.appliedFeeRate)
        assertEquals(BigDecimal("700"), saved.feeAmount)
        assertEquals(BigDecimal("9300"), saved.netAmount)
    }

    @Test
    @DisplayName("FeePolicy가 없을 경우 기본 수수료(3% + 100원)를 적용한다")
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
        assertEquals(200L, result.id)
        assertEquals(0, result.appliedFeeRate.compareTo(BigDecimal("0.03"))) // 스케일 무시
        assertEquals(BigDecimal("400"), result.feeAmount)   // (10000 * 0.03) + 100 = 400
        assertEquals(BigDecimal("9600"), result.netAmount)
        assertEquals(PaymentStatus.APPROVED, result.status)
    }
}
