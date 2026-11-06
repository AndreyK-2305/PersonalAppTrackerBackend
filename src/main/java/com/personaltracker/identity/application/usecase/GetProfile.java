package com.personaltracker.identity.application.usecase;

import com.personaltracker.identity.application.dto.UserProfile;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.exception.UserNotFoundException;
import com.personaltracker.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetProfile {

    private final UserRepository users;

    public GetProfile(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserProfile execute(UserId owner) {
        return users.findById(owner).map(UserProfile::from).orElseThrow(UserNotFoundException::new);
    }
}
