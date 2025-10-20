package net.icewheel.energy.application.scheduling;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.any;

import java.util.List;

import net.icewheel.energy.application.user.model.User;
import net.icewheel.energy.application.user.repository.UserRepository;
import net.icewheel.energy.infrastructure.vendors.tesla.auth.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@link TokenRefreshScheduler} class.
 */
@ExtendWith(MockitoExtension.class)
class TokenRefreshSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private TokenRefreshScheduler tokenRefreshScheduler;

    /**
     * Test that the token refresh is called for each user.
     */
    @Test
    void testProactivelyRefreshTokens() {
        // Arrange
        User user1 = new User();
        user1.setId("user1");
        User user2 = new User();
        user2.setId("user2");
        List<User> users = List.of(user1, user2);

        when(userRepository.findAll()).thenReturn(users);

        // Act
        tokenRefreshScheduler.proactivelyRefreshTokens();

        // Assert
        verify(tokenService).getValidAccessToken("user1");
        verify(tokenService).getValidAccessToken("user2");
    }

    /**
     * Test that the scheduler continues to the next user if one fails.
     */
    @Test
    void testProactivelyRefreshTokens_ContinueOnError() {
        // Arrange
        User user1 = new User();
        user1.setId("user1");
        User user2 = new User();
        user2.setId("user2");
        List<User> users = List.of(user1, user2);

        when(userRepository.findAll()).thenReturn(users);
        doThrow(new RuntimeException("API error")).when(tokenService).getValidAccessToken("user1");

        // Act
        tokenRefreshScheduler.proactivelyRefreshTokens();

        // Assert
        verify(tokenService).getValidAccessToken("user1");
        verify(tokenService).getValidAccessToken("user2");
    }

    /**
     * Test that nothing happens when there are no users.
     */
    @Test
    void testProactivelyRefreshTokens_NoUsers() {
        // Arrange
        when(userRepository.findAll()).thenReturn(List.of());

        // Act
        tokenRefreshScheduler.proactivelyRefreshTokens();

        // Assert
        verify(tokenService, times(0)).getValidAccessToken(any());
    }
}
