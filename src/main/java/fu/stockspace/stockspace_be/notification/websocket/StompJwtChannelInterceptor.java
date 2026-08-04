package fu.stockspace.stockspace_be.notification.websocket;

import fu.stockspace.stockspace_be.auth.security.JwtUtil;
import fu.stockspace.stockspace_be.auth.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Authenticates a STOMP connection from the JWT provided in its CONNECT frame.
 * Browser WebSocket handshakes cannot reliably carry an Authorization header,
 * so authentication is intentionally performed here instead of at the handshake.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    static final String NOTIFICATION_DESTINATION = "/user/queue/notifications";

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())
                || StompCommand.STOMP.equals(accessor.getCommand())) {
            authenticate(message, accessor);
            return message;
        }

        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            return message;
        }

        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Unauthenticated WebSocket session");
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (!NOTIFICATION_DESTINATION.equals(destination)) {
                throw new MessageDeliveryException("Subscription destination is not allowed");
            }
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            throw new MessageDeliveryException("Client messages are not accepted on the notification WebSocket");
        }

        return message;
    }

    private void authenticate(Message<?> message, StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing WebSocket Authorization header");
        }

        String token = authorization.substring("Bearer ".length());
        try {
            String email = jwtUtil.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtUtil.validateToken(token, userDetails)) {
                throw new MessageDeliveryException("Invalid WebSocket JWT");
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            ));
        } catch (MessageDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Rejected WebSocket connection: {}", exception.getMessage());
            throw new MessageDeliveryException(message, exception);
        }
    }
}
