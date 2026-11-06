package com.personaltracker.identity.infrastructure.web;

import com.personaltracker.identity.application.dto.AuthenticateCommand;
import com.personaltracker.identity.application.dto.RegisterUserCommand;
import com.personaltracker.identity.application.dto.UserProfile;
import com.personaltracker.identity.application.usecase.AuthenticateUser;
import com.personaltracker.identity.application.usecase.RegisterUser;
import com.personaltracker.identity.infrastructure.security.SessionLogin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registro e inicio de sesión. El cierre de sesión (POST /logout) lo atiende Spring Security. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegisterUser registerUser;
    private final AuthenticateUser authenticateUser;
    private final SessionLogin sessionLogin;

    AuthController(RegisterUser registerUser, AuthenticateUser authenticateUser, SessionLogin sessionLogin) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.sessionLogin = sessionLogin;
    }

    @GetMapping("/registration")
    RegistrationStatusResponse registrationStatus() {
        return new RegistrationStatusResponse(registerUser.isAvailable());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ProfileResponse register(
            @Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        UserProfile profile = registerUser.execute(
                new RegisterUserCommand(body.email(), body.password(), body.displayName()));
        sessionLogin.start(profile.id(), request, response);
        return ProfileResponse.from(profile);
    }

    @PostMapping("/login")
    ProfileResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        UserProfile profile = authenticateUser.execute(new AuthenticateCommand(body.email(), body.password()));
        sessionLogin.start(profile.id(), request, response);
        return ProfileResponse.from(profile);
    }

    @Schema(requiredProperties = "enabled")
    record RegistrationStatusResponse(boolean enabled) {}

    record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 256) String password) {}

    record RegisterRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 256) String password,
            @NotBlank @Size(max = 100) String displayName) {}
}
