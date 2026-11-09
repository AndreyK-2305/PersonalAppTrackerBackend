package com.personaltracker.identity.application.usecase;

import com.personaltracker.identity.api.PersonNames;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.exception.UserNotFoundException;
import com.personaltracker.identity.domain.model.PersonName;
import com.personaltracker.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publica los nombres y apellidos del perfil para el resto de módulos a través de {@code identity.api}. */
@Service
public class ResolvePersonNames implements PersonNames {

    private final UserRepository users;

    public ResolvePersonNames(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Names of(UserId user) {
        PersonName name = users.findById(user).orElseThrow(UserNotFoundException::new).personName();
        return new Names(name.givenNames(), name.surnames());
    }
}
