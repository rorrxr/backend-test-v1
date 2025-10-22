package im.bigs.pg.application.pg.service

import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.partner.PgType
import org.springframework.stereotype.Component

@Component
class PgService(
    pgClients: List<PgClientOutPort>
) {
    private val byType: Map<PgType, PgClientOutPort> = pgClients.associateBy { it.pgType }

    fun getClient(pgType: PgType): PgClientOutPort =
        byType[pgType] ?: error("No PG client for type=$pgType")
}