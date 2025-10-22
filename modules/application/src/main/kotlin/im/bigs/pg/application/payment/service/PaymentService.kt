package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.calculation.FeeCalculator
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import io.micrometer.core.instrument.MeterRegistry

/**
 * 결제 생성 유스케이스 구현체.
 * - 입력(REST 등) → 도메인/외부PG/영속성 포트를 순차적으로 호출하는 흐름을 담당합니다.
 * - 수수료 정책 조회 및 적용(계산)은 도메인 유틸리티를 통해 수행합니다.
 */
@Service
class PaymentService(
    private val partnerRepository: PartnerOutPort,
    private val feePolicyRepository: FeePolicyOutPort,
    private val paymentRepository: PaymentOutPort,
    private val pgClients: List<PgClientOutPort>,
    private val meterRegistry: MeterRegistry,
) : PaymentUseCase {
    /**
     * 결제 승인/수수료 계산/저장을 순차적으로 수행합니다.
     * - 현재 예시 구현은 하드코드된 수수료(3% + 100)로 계산합니다.
     * - 과제: 제휴사별 수수료 정책을 적용하도록 개선해 보세요.
     */
    override fun pay(command: PaymentCommand): Payment {
        val partner = partnerRepository.findById(command.partnerId)
            ?: throw IllegalArgumentException("Partner not found: ${command.partnerId}")
        require(partner.active) { "Partner is inactive: ${partner.id}" }

        val pgClient = pgClients.firstOrNull { it.supports(partner.id) }
            ?: throw IllegalStateException("No PG client for partner ${partner.id}")

        return try {
            // PG 승인 요청
            val approve = pgClient.approve(
                PgApproveRequest(
                    partnerId = partner.id,
                    amount = command.amount,
                    cardBin = command.cardBin,
                    cardLast4 = command.cardLast4,
                    productName = command.productName,
                ),
            )
            // 제휴사별 수수료 정책 조회
            val policy = feePolicyRepository.findEffectivePolicy(partner.id)
                ?: FeePolicy.default(partner.id)

            // 정책 기반 수수료 계산 (반올림 포함)
            val fee = policy.calculateFee(command.amount)
            val net = command.amount.subtract(fee)

            // Payment 생성 및 저장
            val payment = Payment(
                partnerId = partner.id,
                amount = command.amount,
                appliedFeeRate = policy.percentage,
                feeAmount = fee,
                netAmount = net,
                cardBin = command.cardBin,
                cardLast4 = command.cardLast4,
                approvalCode = approve.approvalCode,
                approvedAt = approve.approvedAt,
                status = PaymentStatus.APPROVED,
            )

            val saved = paymentRepository.save(payment)

            // 결제 성공 카운터 증가
            recordPaymentMetric(partner.id, success = true)

            saved
        }catch (e: Exception) {
            // 결제 실패 카운터 증가
            recordPaymentMetric(command.partnerId, success = false)
            throw e
        }
    }
    /**
     * partnerId별 성공/실패 메트릭을 Micrometer에 기록합니다.
     */
    private fun recordPaymentMetric(partnerId: Long, success: Boolean) {
        val metricName = if (success) "payment_success_total" else "payment_fail_total"
        meterRegistry.counter(metricName, "partnerId", partnerId.toString()).increment()
    }
}
