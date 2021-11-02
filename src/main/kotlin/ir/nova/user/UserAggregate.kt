package ir.nova.user

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import akka.persistence.typed.javadsl.RetentionCriteria
import ir.nova.JnovaProperties

class UserAggregate private constructor(persistenceId: PersistenceId, private val ctx: ActorContext<UserCommands>) :
    EventSourcedBehavior<UserCommands, Events, UserEntity>(persistenceId) {

    companion object {
        const val ENTITY_KEY = "UserAggregate"
        const val PRIMARY_TAG = "UserAggregateTag"
        val ENTITY_TYPE_KEY: EntityTypeKey<UserCommands> = EntityTypeKey.create(UserCommands::class.java, ENTITY_KEY)

        fun create(persistenceId: PersistenceId): Behavior<UserCommands> = Behaviors.setup {
            UserAggregate(persistenceId, it)
        }
    }

    override fun emptyState(): UserEntity? = null

    override fun commandHandler(): CommandHandler<UserCommands, Events, UserEntity> {
        val builder = newCommandHandlerBuilder()
        builder
            .forNullState()
            .onCommand(Register::class.java) { _, command ->
                command
                    .run { UserRegistered(firstName, lastName, email, username) }
                    .let { event ->
                        Effect()
                            .persist(event)
                            .thenReply(command.replyTo) {
                                StatusReply.success(event)
                            }
                    }
            }
            .onCommand(Update::class.java) { _, command ->
                val errorMsg = JnovaProperties
                    .getProperty("ir.nova.user.UserAggregate.userDoesNotExistError")
                Effect().none()
                    .thenReply(command.replyTo) { StatusReply.error(errorMsg) }
            }
            .onAnyCommand { state, command ->
                ctx.log.warn("Received $command but won't process it in state: $state")
                Effect().none()
            }
        builder
            .forNonNullState()
            .onCommand(Register::class.java) { _, command ->
                Effect().none().thenReply(command.replyTo) {
                    val errMSg = JnovaProperties
                        .getProperty("ir.nova.user.UserAggregate.register.duplicate.error")
                    StatusReply.error(errMSg)
                }
            }
            .onCommand(Update::class.java) { _, command ->
                command
                    .run { UserUpdated(firstName, lastName, email, username) }
                    .let { event ->
                        Effect()
                            .persist(event)
                            .thenReply(command.replyTo) { StatusReply.success(event) }
                    }
            }
            .onAnyCommand { state, command ->
                ctx.log.warn("Received $command but won't process it in state: $state")
                Effect().none()
            }
        return builder.build()
    }

    override fun eventHandler(): EventHandler<UserEntity, Events> {
        val eventBuilder = newEventHandlerBuilder()
        eventBuilder
            .forNullState()
            .onEvent(UserRegistered::class.java) { _, event ->
                event.run { UserEntity(firstName, lastName, email, username) }
            }
            .onAnyEvent { state, event ->
                ctx.log.warn("Received $event but won't process it in state: $state")
                state
            }
        eventBuilder
            .forNonNullState()
            .onEvent(UserUpdated::class.java) { state, event ->
                event.run { UserEntity(event.firstName, event.lastName, event.email, state.username) }
            }
            .onAnyEvent { state, event ->
                ctx.log.warn("Received $event but won't process it in state: $state")
                state
            }
        return eventBuilder.build()
    }

    override fun tagsFor(event: Events?): MutableSet<String> {
        return mutableSetOf(PRIMARY_TAG)
    }

    override fun retentionCriteria(): RetentionCriteria =
        RetentionCriteria.snapshotEvery(100, 2)

    override fun shouldSnapshot(state: UserEntity?, event: Events?, sequenceNr: Long): Boolean {
        return super.shouldSnapshot(state, event, sequenceNr)
    }
}