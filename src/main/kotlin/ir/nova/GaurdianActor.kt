package ir.nova

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import ir.nova.user.ProjectionHandlerImpl
import ir.nova.user.UserAggregate

class GuardianActor private constructor(actorContext: ActorContext<Void>) :
    AbstractBehavior<Void>(actorContext) {

    companion object {
        fun create(): Behavior<Void> = Behaviors.setup { ctx -> GuardianActor(ctx) }
    }

    init {
        actorContext.spawn(
            ProjectionBehavior.create(ProjectionHandlerImpl.createProjection(actorContext.system)),
            "UserProjection"
        )

        ClusterSharding
            .get(actorContext.system)
            .init(Entity.of(UserAggregate.ENTITY_TYPE_KEY) {
                UserAggregate.create(PersistenceId.of(it.entityTypeKey.name(), it.entityId))
            })
    }

    override fun createReceive(): Receive<Void> {
        return newReceiveBuilder().build()
    }
}