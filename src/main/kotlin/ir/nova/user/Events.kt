package ir.nova.user

import ir.nova.CborSerialization

sealed interface Events: CborSerialization

data class UserRegistered(val firstName: String, val lastName: String, val email: Email, val username: Username): Events
data class UserUpdated(val firstName: String, val lastName: String, val email: Email, val username: Username): Events