package com.medibridge.core.network

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val username: String,
    val password: String,
    val name: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthUserDto(
    val id: String,
    val username: String,
    val name: String
)

data class AuthResponse(
    val token: String,
    val user: AuthUserDto
)
