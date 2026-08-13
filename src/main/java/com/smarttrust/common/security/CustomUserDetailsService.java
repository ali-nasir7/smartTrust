package com.smarttrust.common.security;

import com.smarttrust.modules.user.infrastructure.persistence.UserRepository;
import com.smarttrust.modules.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with phone: " + phone));

        return new org.springframework.security.core.userdetails.User(
                user.getPhone(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    public UserPrincipal loadPrincipalById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User id not found"));
        return UserPrincipal.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .phoneVerified(user.isPhoneVerified())
                .build();
    }
}
