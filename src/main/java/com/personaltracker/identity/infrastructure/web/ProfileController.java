package com.personaltracker.identity.infrastructure.web;

import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.identity.application.dto.ChangePasswordCommand;
import com.personaltracker.identity.application.dto.UpdateProfileCommand;
import com.personaltracker.identity.application.usecase.ChangePassword;
import com.personaltracker.identity.application.usecase.GetProfile;
import com.personaltracker.identity.application.usecase.UpdateProfile;
import com.personaltracker.shared.domain.UserId;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cuenta del usuario autenticado. El frontend usa GET /me para saber si hay una sesión activa. */
@RestController
@RequestMapping("/api/v1/me")
class ProfileController {

    private final GetProfile getProfile;
    private final UpdateProfile updateProfile;
    private final ChangePassword changePassword;

    ProfileController(GetProfile getProfile, UpdateProfile updateProfile, ChangePassword changePassword) {
        this.getProfile = getProfile;
        this.updateProfile = updateProfile;
        this.changePassword = changePassword;
    }

    @GetMapping
    ProfileResponse get(@CurrentUser UserId owner) {
        return ProfileResponse.from(getProfile.execute(owner));
    }

    /** Actualización parcial: los campos ausentes no cambian; nombres o apellidos vacíos se borran. */
    @PatchMapping
    ProfileResponse update(@CurrentUser UserId owner, @Valid @RequestBody UpdateProfileRequest body) {
        return ProfileResponse.from(
                updateProfile.execute(new UpdateProfileCommand(
                        owner, body.displayName(), body.timeZone(), body.givenNames(), body.surnames())));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(
            @CurrentUser UserId owner, @Valid @RequestBody ChangePasswordRequest body, HttpSession session) {
        changePassword.execute(new ChangePasswordCommand(
                owner, body.currentPassword(), body.newPassword(), session.getId()));
    }

    record UpdateProfileRequest(
            @Size(min = 1, max = 100) String displayName,
            @Size(max = 64) String timeZone,
            @Size(max = 100) String givenNames,
            @Size(max = 100) String surnames) {}

    record ChangePasswordRequest(
            @NotBlank @Size(max = 256) String currentPassword,
            @NotBlank @Size(max = 256) String newPassword) {}
}
