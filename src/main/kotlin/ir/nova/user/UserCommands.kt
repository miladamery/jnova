package ir.nova.user

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import ir.nova.CborSerialization

sealed interface UserCommands : CborSerialization

data class Register(
    val firstName: String,
    val lastName: String,
    val email: Email,
    val username: Username,
    val replyTo: ActorRef<StatusReply<UserRegistered>>
) : UserCommands

data class Update(
    val firstName: String,
    val lastName: String,
    val email: Email,
    val username: Username,
    val replyTo: ActorRef<StatusReply<UserUpdated>>
) : UserCommands