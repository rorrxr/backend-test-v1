package im.bigs.pg.application.payment.service

import im.bigs.pg.application.payment.port.`in`.*
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.payment.port.out.PaymentQuery
import im.bigs.pg.application.payment.port.out.PaymentSummaryFilter
import im.bigs.pg.domain.payment.PaymentSummary
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

/**
 * 결제 이력 조회 유스케이스 구현체.
 * - 커서 토큰은 createdAt/id를 안전하게 인코딩해 전달/복원합니다.
 * - 통계는 조회 조건과 동일한 집합을 대상으로 계산됩니다.
 */
@Service
class QueryPaymentsService(
    private val paymentOutPort: PaymentOutPort
) : QueryPaymentsUseCase {
    /**
     * 필터를 기반으로 결제 내역을 조회합니다.
     *
     * 현재 구현은 과제용 목업으로, 빈 결과를 반환합니다.
     * 지원자는 커서 기반 페이지네이션과 통계 집계를 완성하세요.
     *
     * @param filter 파트너/상태/기간/커서/페이지 크기
     * @return 조회 결과(목록/통계/커서)
     */
    override fun query(filter: QueryFilter): QueryResult {
        // 커서 디코드
        val (cursorCreatedAt, cursorId) = decodeCursor(filter.cursor)

        // infra로 전달할 Query 객체 구성
        val query = PaymentQuery(
            partnerId = filter.partnerId,
            status = filter.status?.let { im.bigs.pg.domain.payment.PaymentStatus.valueOf(it.uppercase()) },
            from = filter.from,
            to = filter.to,
            limit = filter.limit,
            cursorCreatedAt = cursorCreatedAt?.atZone(ZoneOffset.UTC)?.toLocalDateTime(),
            cursorId = cursorId
        )

        // 페이징 조회
        val page = paymentOutPort.findBy(query)

        // nextCursor 생성
        val nextCursor = encodeCursor(
            page.nextCursorCreatedAt?.toInstant(ZoneOffset.UTC),
            page.nextCursorId
        )

        // 통계 조회
        val summaryProjection = paymentOutPort.summary(
            PaymentSummaryFilter(
                partnerId = filter.partnerId,
                status = filter.status?.let { im.bigs.pg.domain.payment.PaymentStatus.valueOf(it.uppercase()) },
                from = filter.from,
                to = filter.to
            )
        )

        val summary = PaymentSummary(
            count = summaryProjection.count,
            totalAmount = summaryProjection.totalAmount,
            totalNetAmount = summaryProjection.totalNetAmount
        )

        // 결과 반환
        return QueryResult(
            items = page.items,
            summary = summary,
            nextCursor = nextCursor,
            hasNext = page.hasNext
        )
    }


    /** 다음 페이지 이동을 위한 커서 인코딩. */
    private fun encodeCursor(createdAt: Instant?, id: Long?): String? {
        if (createdAt == null || id == null) return null
        val raw = "${createdAt.toEpochMilli()}:$id"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    /** 요청으로 전달된 커서 복원. 유효하지 않으면 null 커서로 간주합니다. */
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
