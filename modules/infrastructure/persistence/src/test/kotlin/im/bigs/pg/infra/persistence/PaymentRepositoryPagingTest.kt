package im.bigs.pg.infra.persistence

import im.bigs.pg.infra.persistence.config.JpaConfig
import im.bigs.pg.infra.persistence.payment.entity.PaymentEntity
import im.bigs.pg.infra.persistence.payment.repository.PaymentJpaRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ContextConfiguration
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@ContextConfiguration(classes = [JpaConfig::class])
class 결제저장소커서페이징Test @Autowired constructor(
    val paymentRepo: PaymentJpaRepository,
) {

    @Test
    @DisplayName("커서 기반 페이징과 summary가 일관되어야 한다")
    fun `커서 기반 페이징과 summary가 일관되어야 한다`() {
        // given: 테스트용 결제 데이터 35건 삽입
        val baseTs = Instant.parse("2024-01-01T00:00:00Z")
        repeat(35) { i ->
            paymentRepo.save(
                PaymentEntity(
                    partnerId = 1L,
                    amount = BigDecimal("1000"),
                    appliedFeeRate = BigDecimal("0.0300"),
                    feeAmount = BigDecimal("30"),
                    netAmount = BigDecimal("970"),
                    cardBin = null,
                    cardLast4 = "%04d".format(i),
                    approvalCode = "A$i",
                    approvedAt = baseTs.plusSeconds(i.toLong()),
                    status = "APPROVED",
                    createdAt = baseTs.plusSeconds(i.toLong()),
                    updatedAt = baseTs.plusSeconds(i.toLong()),
                ),
            )
        }

        // when: 1페이지 조회 (limit = 20)
        val firstPage = paymentRepo.pageBy(
            partnerId = 1L,
            status = "APPROVED",
            fromAt = null,
            toAt = null,
            cursorCreatedAt = null,
            cursorId = null,
            pageable = PageRequest.of(0, 21) // limit + 1 로 다음 페이지 여부 판단
        )

        assertEquals(21, firstPage.size)
        val hasNext = firstPage.size > 20
        assertTrue(hasNext)

        // then: 1페이지 내 마지막 데이터 기준으로 nextCursor 생성
        val lastOfFirst = firstPage[19]
        val nextCursor = encodeCursor(lastOfFirst.createdAt, lastOfFirst.id!!)
        assertTrue(nextCursor.isNotBlank())

        // and: 커서 복원 → 2페이지 조회
        val (cursorCreatedAt, cursorId) = decodeCursor(nextCursor)
        val secondPage = paymentRepo.pageBy(
            partnerId = 1L,
            status = "APPROVED",
            fromAt = null,
            toAt = null,
            cursorCreatedAt = cursorCreatedAt,
            cursorId = cursorId,
            pageable = PageRequest.of(0, 21),
        )
        assertTrue(secondPage.isNotEmpty())

        // and: 2페이지 데이터가 1페이지와 겹치지 않아야 함
        val firstIds = firstPage.mapNotNull { it.id }.toSet()
        val secondIds = secondPage.mapNotNull { it.id }.toSet()
        assertTrue(firstIds.intersect(secondIds).isEmpty(), "페이지 간 중복 데이터가 없어야 함")

        // and: summary 계산 일관성 검증
        val summaryList = paymentRepo.summary(1L, "APPROVED", null, null)
        val row = summaryList.first()
        val count = (row[0] as Number).toLong()
        val totalAmount = row[1] as BigDecimal
        val totalNet = row[2] as BigDecimal

        assertEquals(35L, count)
        assertEquals(BigDecimal("35000"), totalAmount)
        assertEquals(BigDecimal("33950"), totalNet)
    }

    /** Base64 커서 인코딩 (Service 로직 동일) */
    private fun encodeCursor(createdAt: Instant, id: Long): String {
        val raw = "${createdAt.toEpochMilli()}:$id"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    /** Base64 커서 디코딩 (Service 로직 동일) */
    private fun decodeCursor(cursor: String?): Pair<Instant?, Long?> {
        if (cursor.isNullOrBlank()) return null to null
        return try {
            val raw = String(Base64.getUrlDecoder().decode(cursor))
            val parts = raw.split(":")
            val ts = parts[0].toLong()
            val id = parts[1].toLong()
            Instant.ofEpochMilli(ts) to id
        } catch (e: Exception) {
            null to null
        }
    }
}
