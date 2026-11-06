package com.personaltracker.identity.application.dto;

public record RegisterUserCommand(String email, String password, String displayName) {}
