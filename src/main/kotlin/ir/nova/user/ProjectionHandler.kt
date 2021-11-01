package ir.nova.user

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.cassandra.javadsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.javadsl.EventSourcedProvider
import akka.projection.javadsl.AtLeastOnceProjection
import akka.projection.javadsl.Handler
import akka.stream.alpakka.cassandra.javadsl.CassandraSession
import akka.stream.alpakka.cassandra.javadsl.CassandraSessionRegistry
import ir.nova.Consts
import java.time.Duration
import java.util.concurrent.CompletionStage


interface ProjectionHandler {
    fun updateUser(event: Events): CompletionStage<Done>
}

class ProjectionHandlerImpl(private val session: CassandraSession) : ProjectionHandler,
    Handler<EventEnvelope<Events>>() {

    companion object {

        val projectionId: ProjectionId = ProjectionId.of(UserAggregate.ENTITY_KEY, UserAggregate.PRIMARY_TAG)

        fun createProjection(system: ActorSystem<*>): AtLeastOnceProjection<Offset, EventEnvelope<Events>> {
            val session: CassandraSession = CassandraSessionRegistry
                .get(system)
                .sessionFor("akka.projection.cassandra.session-config")
            return CassandraProjection.atLeastOnce(
                projectionId,
                EventSourcedProvider.eventsByTag(
                    system,
                    CassandraReadJournal.Identifier(),
                    UserAggregate.PRIMARY_TAG
                )
            )
            { ProjectionHandlerImpl(session) }
                .withSaveOffset(100, Duration.ofMillis(500))
        }
    }

    override fun updateUser(event: Events): CompletionStage<Done> {
        when (event) {
            is UserRegistered -> {
                val cql = "UPDATE ${Consts.APPLICATION_KEYSPACE}.${Consts.USER_TABLE} SET firstName = ?, lastName = ?, email = ? WHERE username = ?"
                return session.executeWrite(
                    cql,
                    event.firstName,
                    event.lastName,
                    event.email.value,
                    event.username.value
                )
            }
            is UserUpdated -> {
                val cql = "UPDATE ${Consts.APPLICATION_KEYSPACE}.${Consts.USER_TABLE} SET firstName = ?, lastName = ?, email = ? WHERE username = ?"
                return session.executeWrite(
                    cql,
                    event.firstName,
                    event.lastName,
                    event.email.value,
                    event.username.value
                )
            }
        }
    }

    override fun process(envelope: EventEnvelope<Events>): CompletionStage<Done> {
        return updateUser(envelope.event())
    }

}