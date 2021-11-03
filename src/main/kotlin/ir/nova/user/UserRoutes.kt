package ir.nova.user

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.http.javadsl.marshallers.jackson.Jackson
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.StatusCode
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.server.AllDirectives
import akka.http.javadsl.server.PathMatchers
import akka.http.javadsl.server.Route
import akka.pattern.StatusReply
import akka.stream.alpakka.cassandra.javadsl.CassandraSource
import akka.stream.javadsl.Sink
import com.fasterxml.jackson.databind.ObjectMapper
import ir.nova.config.Cassandra
import ir.nova.config.Const
import jakarta.validation.Path
import jakarta.validation.Validator
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@Component
class UserRoutes(
    private val system: ActorSystem<*>,
    private val cassandra: Cassandra,
    private val validator: Validator,
    private val objectMapper: ObjectMapper,
    private val sharding: ClusterSharding
) : AllDirectives() {
    private val askDuration = Duration.ofMinutes(2)

    data class RegisterRequest(
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.firstName.NotBlank}")
        val firstName: String,
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.lastName.NotBlank}")
        val lastName: String,
        @get:Email(message = "{ir.nova.user.UserRoutes.RegisterRequest.email.Email}")
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.email.NotBlank}")
        val email: String,
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.username.NotBlank}")
        val username: String,
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.password.NotBlank}")
        @get:Size(min = 8, message = "{ir.nova.user.UserRoutes.RegisterRequest.password.min.Size}")
        val password: String
    )

    data class LoadRequest(
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.username.NotBlank}")
        val username: String
    )

    data class UpdateRequest(
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.firstName.NotBlank}")
        val firstName: String,
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.lastName.NotBlank}")
        val lastName: String,
        @get:Email(message = "{ir.nova.user.UserRoutes.RegisterRequest.email.Email}")
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.email.NotBlank}")
        val email: String,
        @get:NotBlank(message = "{ir.nova.user.UserRoutes.RegisterRequest.username.NotBlank}")
        val username: String
    )

    fun routes(): Route = pathPrefix("user") {
        concat(
            pathEndOrSingleSlash {
                concat(registerUserRoute(), updateUserRoute())
            },
            loadUserRoute(),
            loadAllUsers()
        )
    }

    private fun registerUserRoute(): Route = post {
        entity(Jackson.unmarshaller(objectMapper, RegisterRequest::class.java)) { request ->
            onSuccess(registerRequestHandler(request)) { response ->
                complete(response)
            }
        }
    }

    private fun loadUserRoute(): Route = get {
        pathPrefix("load") {
            path(PathMatchers.segment()) { username ->
                onSuccess(loadRequestHandler(LoadRequest(username))) { response ->
                    complete(response)
                }
            }
        }
    }

    private fun updateUserRoute(): Route = put {
        entity(Jackson.unmarshaller(objectMapper, UpdateRequest::class.java)) { request ->
            onSuccess(updateRequestHandler(request)) { response -> complete(response) }
        }
    }

    private fun loadAllUsers(): Route = get {
        pathPrefix("all") {
            onSuccess(loadAllUsersRequestHandler()) { response ->
                complete(response)
            }
        }
    }

    private fun registerRequestHandler(registerRequest: RegisterRequest): CompletionStage<HttpResponse?> =
        userAggregateAskRequestHandlerBuilder<UserRegistered>(
            registerRequest,
            registerRequest.username,
            { userRegistered -> createdResponse(userRegistered) },
            { exception -> badRequestResponse(exception.message.toString()) }
        ) { ref ->
            Register(
                registerRequest.firstName,
                registerRequest.lastName,
                ir.nova.user.Email(registerRequest.email),
                Username(registerRequest.username),
                Password(registerRequest.password),
                ref
            )
        }

    private fun updateRequestHandler(updateRequest: UpdateRequest): CompletionStage<HttpResponse?> =
        userAggregateAskRequestHandlerBuilder<UserUpdated>(
            updateRequest,
            updateRequest.username,
            { okResponse(it) },
            { notFoundResponse() }
        ) { ref ->
            Update(
                updateRequest.firstName,
                updateRequest.lastName,
                ir.nova.user.Email(updateRequest.email),
                Username(updateRequest.username),
                ref
            )
        }

    private fun loadRequestHandler(loadRequest: LoadRequest): CompletionStage<HttpResponse?> {
        val errors = validate(loadRequest)
        return if (errors != null) {
            CompletableFuture.completedFuture(badRequestResponse(errors))
        } else {
            CassandraSource
                .create(
                    cassandra.session,
                    "SELECT * FROM ${Const.APPLICATION_KEYSPACE}.${Const.USER_TABLE} WHERE username = ?",
                    loadRequest.username
                )
                .limit(1)
                .map { row ->
                    UserDto(
                        row.getString("firstname")!!,
                        row.getString("lastname")!!,
                        ir.nova.user.Email(row.getString("email")!!),
                        Username(row.getString("username")!!)
                    )
                }
                .map { okResponse(it) }
                .runWith(Sink.head(), system)
                .exceptionally { notFoundResponse() }
        }
    }

    private fun loadAllUsersRequestHandler(): CompletionStage<HttpResponse> =
        CassandraSource
            .create(
                cassandra.session,
                "SELECT * FROM ${Const.APPLICATION_KEYSPACE}.${Const.USER_TABLE}"
            )
            .map { row ->
                UserDto(
                    row.getString("firstname")!!,
                    row.getString("lastname")!!,
                    ir.nova.user.Email(row.getString("email")!!),
                    Username(row.getString("username")!!)
                )
            }
            .runFold(
                mutableListOf<UserDto>(),
                { list, entity ->
                    list.add(entity)
                    list
                }, system
            )
            .thenApply { users -> okResponse(users) }

    private inline fun <reified T> userAggregateAskRequestHandlerBuilder(
        request: Any,
        entityId: String,
        noinline successResponse: (T) -> HttpResponse,
        noinline exceptionResponse: (Throwable) -> HttpResponse,
        noinline askWithStatusLambda: (ActorRef<StatusReply<T>>) -> UserCommands
    ): CompletionStage<HttpResponse?> {
        val errors = validate(request)
        return if (errors != null) {
            CompletableFuture.completedFuture(badRequestResponse(errors))
        } else {
            sharding
                .entityRefFor(UserAggregate.ENTITY_TYPE_KEY, entityId)
                .askWithStatus(askWithStatusLambda, askDuration)
                .thenApply(successResponse)
                .exceptionally(exceptionResponse)
        }
    }

    private fun validate(toValidate: Any): Map<Path, String>? {
        val constraints = validator.validate(toValidate)
        return if (constraints.isNotEmpty()) {
            constraints.associate {
                it.propertyPath to it.message
            }
        } else {
            null
        }
    }

    private fun createdResponse(entity: Any) = httpResponse(StatusCodes.CREATED, entity)

    private fun notFoundResponse() = HttpResponse.create().withStatus(StatusCodes.NOT_FOUND)

    private fun okResponse(entity: Any): HttpResponse =
        httpResponse(StatusCodes.OK, entity)

    private fun badRequestResponse(entity: Any): HttpResponse =
        httpResponse(StatusCodes.BAD_REQUEST, mapOf("error" to entity))

    private fun httpResponse(statusCodes: StatusCode, entity: Any) =
        HttpResponse.create().withStatus(statusCodes).withEntity(objectMapper.writeValueAsString(entity))
}