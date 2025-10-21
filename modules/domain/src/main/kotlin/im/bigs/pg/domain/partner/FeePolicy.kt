package im.bigs.pg.domain.partner

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/**
 * 제휴사별 수수료 정책.
 * - effectiveFrom 시점부터 적용되는 정책으로, 동일 제휴사에 여러 버전이 존재할 수 있습니다.
 * - percentage(비율)와 fixedFee(고정 수수료)를 함께/단독으로 사용할 수 있습니다.
 * - 시간대는 UTC 기준을 권장합니다.
 *
 * @property partnerId 정책이 적용될 제휴사 식별자
 * @property effectiveFrom 정책 유효 시작 시점(UTC)
 * @property percentage 비율 수수료(예: 0.0235 = 2.35%)
 * @property fixedFee 고정 수수료(없으면 null)
 */
data class FeePolicy(
    val id: Long? = null,
    val partnerId: Long,
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val effectiveFrom: LocalDateTime,
    val percentage: BigDecimal, // e.g., 0.0235 (2.35%)
    val fixedFee: BigDecimal? = null,
) {
    /**
     * 수수료 금액 계산
     * calculateFee() 내부에서 소수점 2자리, HALF_UP 반올림 적용
     * fallback 정책은 default() companion 함수로 제공
     * FeeCalculator 대신 도메인 객체 자체에 계산 책임 위임
     */
    fun calculateFee(amount: BigDecimal): BigDecimal {
        // percentage, fixedFee 모두 BigDecimal로 정확히 계산
        val variableFee = amount.multiply(percentage)
            .setScale(4, RoundingMode.HALF_UP) // 중간 계산 시 4자리 유지
        val fixed = fixedFee ?: BigDecimal.ZERO
        val totalRaw = variableFee.add(fixed)
        val totalFinal = totalRaw.setScale(2, RoundingMode.HALF_UP)

        // 디버그 로그 출력
        println(
            """
            [FeePolicy Debug]
            - amount       : $amount
            - percentage   : $percentage
            - fixedFee     : $fixed
            - variableFee  : $variableFee
            - total (raw)  : $totalRaw
            - total (final): $totalFinal
            """.trimIndent()
        )

        return totalFinal
    }

    companion object {
        /** 정책 미존재 시 기본 수수료 정책 (3% + 100원) */
        fun default(partnerId: Long): FeePolicy = FeePolicy(
            partnerId = partnerId,
            effectiveFrom = LocalDateTime.MIN,
            percentage = BigDecimal("0.0300"),
            fixedFee = BigDecimal("100")
        )
    }

}
