package im.bigs.pg.application.pg.port.out

import im.bigs.pg.domain.partner.PgType

/** 외부 결제사(PG) 승인 연동 포트. */
interface PgClientOutPort {
    val pgType: PgType
    fun approve(request: PgApproveRequest): PgApproveResult
}
