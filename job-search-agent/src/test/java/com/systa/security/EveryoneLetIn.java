package com.systa.security;

import com.systa.model.UserAccess.Status;
import com.systa.service.AccessService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * For controller tests that aren't about access: every signed-in user is let in. The access check itself
 * (AccessWebConfig) is still in place, as in the running app; AccessControllerTest covers turning users away.
 */
@TestConfiguration
public class EveryoneLetIn {

    @Bean
    public AccessService accessService() {
        final AccessService accessService = mock(AccessService.class);
        when(accessService.statusOf(any(), any())).thenReturn(Status.APPROVED);
        when(accessService.mayUseApp(any())).thenReturn(true);
        return accessService;
    }
}
