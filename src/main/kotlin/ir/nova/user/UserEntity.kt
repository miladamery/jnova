package ir.nova.user

import com.fasterxml.jackson.annotation.JsonValue

data class UserEntity(
    val firstName: String,
    val lastName: String,
    val email: Email,
    val username: Username
)

data class Email(@get:JsonValue val value: String)

data class Username(@get:JsonValue val value: String)
