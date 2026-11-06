package com.personaltracker.identity.application.dto;

public record AuthenticateCommand(String email, String password) {}
