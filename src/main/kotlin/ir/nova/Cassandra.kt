package ir.nova

import akka.actor.typed.ActorSystem
import akka.stream.alpakka.cassandra.CassandraSessionSettings
import akka.stream.alpakka.cassandra.javadsl.CassandraSession
import akka.stream.alpakka.cassandra.javadsl.CassandraSessionRegistry

object Cassandra {
    lateinit var system: ActorSystem<*>
    val session: CassandraSession by lazy {
        CassandraSessionRegistry
            .get(system)
            .sessionFor(CassandraSessionSettings.create())
    }
}