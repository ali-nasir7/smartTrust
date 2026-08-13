package com.smarttrust.modules.auth.domain.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserRegisteredEvent extends ApplicationEvent {

    private final Long userId;
    private final String phone;
    private final String role;

    // For mock notification (FYP only, not for prod logging)
    private final transient String plainOtpForMock;

    public UserRegisteredEvent(Object source, Long userId, String phone, String role, String plainOtpForMock) {
        super(source);
        this.userId = userId;
        this.phone = phone;
        this.role = role;
        this.plainOtpForMock = plainOtpForMock;
    }
}
