package com.smartwealth.ai.service;

import com.smartwealth.ai.api.response.UserProfileView;
import com.smartwealth.ai.repository.UserProfileRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserProfileQueryService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileQueryService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public List<UserProfileView> listUsers() {
        return userProfileRepository.findAll().stream()
                .map(user -> new UserProfileView(user.getId(), user.getFullName(), user.getRiskLevel()))
                .toList();
    }
}
