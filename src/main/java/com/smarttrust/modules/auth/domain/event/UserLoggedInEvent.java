package com.smarttrust.modules.auth.domain.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserLoggedInEvent extends ApplicationEvent {

    private final Long userId;
    private final String phone;
    private final String ipAddress;

    public UserLoggedInEvent(Object source, Long userId, String phone, String ipAddress) {
        super(source);
        this.userId = userId;
        this.phone = phone;
        this.ipAddress = ipAddress;
    }
}
