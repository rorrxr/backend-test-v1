package im.bigs.pg.domain.calculation

import org.junit.jupiter.api.DisplayName
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * FeeCalculator 단위 테스트
 *
 * 검증 포인트
 * 퍼센트 수수료만 적용 시 반올림 및 정산금 계산 정확성
 * 퍼센트 + 정액 수수료 병행 적용 시 합산 정확성
 */
class 수수료계산기Test {

    @Test
    @DisplayName("퍼센트 수수료만 적용 시 반올림 및 정산금이 정확해야 한다")
    fun `퍼센트 수수료만 적용 시 반올림 및 정산금이 정확해야 한다`() {
        val amount = BigDecimal("10000")
        val rate = BigDecimal("0.0235")
        val (fee, net) = FeeCalculator.calculateFee(amount, rate, null)

        // 10000 * 0.0235 = 235 → 반올림 후 fee=235, net=9765
        assertEquals(BigDecimal("235"), fee)
        assertEquals(BigDecimal("9765"), net)
    }

    @Test
    @DisplayName("퍼센트+정액 수수료가 함께 적용되어야 한다")
    fun `퍼센트와 정액 수수료가 함께 적용되어야 한다`() {
        val amount = BigDecimal("10000")
        val rate = BigDecimal("0.0300")
        val fixed = BigDecimal("100")

        val (fee, net) = FeeCalculator.calculateFee(amount, rate, fixed)

        // (10000 * 0.03) + 100 = 400, net = 9600
        assertEquals(BigDecimal("400"), fee)
        assertEquals(BigDecimal("9600"), net)
    }
}
