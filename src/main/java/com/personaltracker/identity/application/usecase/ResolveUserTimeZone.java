package com.personaltracker.identity.application.usecase;

import com.personaltracker.identity.api.UserTimeZones;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.exception.UserNotFoundException;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publica la zona horaria del perfil para el resto de módulos a través de {@code identity.api}. */
@Service
public class ResolveUserTimeZone implements UserTimeZones {

    private final UserRepository users;

    public ResolveUserTimeZone(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneId of(UserId user) {
        return users.findById(user).map(User::timeZone).orElseThrow(UserNotFoundException::new);
    }
}
