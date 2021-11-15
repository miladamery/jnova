package ir.nova

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.http.javadsl.Http
import akka.http.javadsl.server.AllDirectives
import akka.http.javadsl.server.Route
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import ir.nova.user.UserRoutes
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan("ir.nova")
open class Application : AllDirectives() {

    @Bean
    open fun applicationRoutes(userRoutes: UserRoutes): Route =
        concat(
            userRoutes.routes(),
            getFromResourceDirectory("webapp/register")
        )


    @Bean
    open fun actorSystem(): ActorSystem<Void> =
        ActorSystem.create(GuardianActor.create(), "ClusterSystem")

    @Bean
    open fun httpServer(actorSystem: ActorSystem<Void>, userRoutes: UserRoutes): Http =
        Http
            .get(actorSystem)
            .apply {
                newServerAt("localhost", 8086)
                    .bind(applicationRoutes(userRoutes))
            }

    @Bean
    open fun beanValidator(): Validator = Validation.buildDefaultValidatorFactory().validator

    @Bean
    open fun objectMapper(): ObjectMapper = jacksonObjectMapper()

    @Bean
    open fun clusterSharding(actorSystem: ActorSystem<Void>): ClusterSharding = ClusterSharding.get(actorSystem)
}

fun main() {
    AnnotationConfigApplicationContext(Application::class.java)
}