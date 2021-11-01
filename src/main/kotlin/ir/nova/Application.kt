package ir.nova

import akka.actor.typed.ActorSystem
import akka.http.javadsl.Http
import ir.nova.user.UserRoutes

fun main() {
    val system = ActorSystem.create(GuardianActor.create(), "ClusterSystem")
    Cassandra.system = system

    Http
        .get(system)
        .apply {
            newServerAt("localhost", 8086)
                .bind(UserRoutes(system).routes())
        }
}