package ir.nova.config

import akka.actor.typed.ActorSystem
import akka.stream.alpakka.cassandra.CassandraSessionSettings
import akka.stream.alpakka.cassandra.javadsl.CassandraSession
import akka.stream.alpakka.cassandra.javadsl.CassandraSessionRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class Cassandra(private var system: ActorSystem<*>) {

    val session: CassandraSession by lazy {
        CassandraSessionRegistry
            .get(system)
            .sessionFor(CassandraSessionSettings.create())
    }
}