package fu.stockspace.stockspace_be.notification.websocket;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.security.JwtUtil;
import fu.stockspace.stockspace_be.auth.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompJwtChannelInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @InjectMocks
    private StompJwtChannelInterceptor interceptor;

    @Test
    void preSend_ConnectWithValidJwt_AssignsTheAuthenticatedUser() {
        User user = User.builder().email("tenant@test.com").build();
        when(jwtUtil.extractEmail("valid-token")).thenReturn("tenant@test.com");
        when(userDetailsService.loadUserByUsername("tenant@test.com")).thenReturn(user);
        when(jwtUtil.validateToken("valid-token", user)).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        // Spring leaves inbound STOMP headers mutable until the interceptor chain completes.
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertEquals("tenant@test.com", StompHeaderAccessor.wrap(result).getUser().getName());
    }

    @Test
    void preSend_ConnectWithoutJwt_IsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void preSend_SubscribeOutsidePrivateNotificationQueue_IsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/queue/other");
        accessor.setUser(() -> "tenant@test.com");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, null));
    }
}
