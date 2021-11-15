package ir.nova.user

import org.mapstruct.Mapper

/*@Mapper*/
interface UserConverter {
    fun convertToDto(userEntity: UserEntity): UserDto
}