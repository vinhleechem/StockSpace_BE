package fu.stockspace.stockspace_be.notification.websocket;

import fu.stockspace.stockspace_be.auth.security.JwtUtil;
import fu.stockspace.stockspace_be.auth.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;





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
            throw reject(
                    message,
                    accessor,
                    "WS_AUTH_REQUIRED",
                    "Authenticated STOMP session required",
                    null
            );
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (!NOTIFICATION_DESTINATION.equals(destination)) {
                throw reject(
                        message,
                        accessor,
                        "WS_DESTINATION_FORBIDDEN",
                        "Subscription destination is not allowed",
                        null
                );
            }
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            throw reject(
                    message,
                    accessor,
                    "WS_SEND_FORBIDDEN",
                    "Client messages are not accepted on this WebSocket",
                    null
            );
        }

        return message;
    }

    private void authenticate(Message<?> message, StompHeaderAccessor accessor) {
        String authorization = findAuthorizationHeader(accessor);
        if (authorization == null || authorization.isBlank()) {
            throw reject(
                    message,
                    accessor,
                    "WS_AUTH_MISSING",
                    "Missing WebSocket Authorization header",
                    null
            );
        }

        String[] authorizationParts = authorization.trim().split("\\s+", 2);
        if (authorizationParts.length != 2
                || !"Bearer".equalsIgnoreCase(authorizationParts[0])
                || authorizationParts[1].isBlank()) {
            throw reject(
                    message,
                    accessor,
                    "WS_AUTH_MALFORMED",
                    "Malformed WebSocket Authorization header",
                    null
            );
        }

        String token = authorizationParts[1].trim();
        try {
            String email = jwtUtil.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtUtil.validateToken(token, userDetails)) {
                throw reject(
                        message,
                        accessor,
                        "WS_AUTH_INVALID",
                        "Invalid or expired WebSocket JWT",
                        null
                );
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            ));
        } catch (StompClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw reject(
                    message,
                    accessor,
                    "WS_AUTH_INVALID",
                    "Invalid or expired WebSocket JWT",
                    exception
            );
        }
    }

    private String findAuthorizationHeader(StompHeaderAccessor accessor) {
        for (Map.Entry<String, List<String>> header : accessor.toNativeHeaderMap().entrySet()) {
            if (!"Authorization".equalsIgnoreCase(header.getKey())) {
                continue;
            }

            return header.getValue().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private StompClientException reject(Message<?> message,
                                        StompHeaderAccessor accessor,
                                        String code,
                                        String clientMessage,
                                        Throwable cause) {
        log.warn(
                "[WebSocket] Rejected STOMP frame sessionId={} command={} code={} causeType={}",
                accessor.getSessionId(),
                accessor.getCommand(),
                code,
                cause == null ? "none" : cause.getClass().getSimpleName()
        );
        return new StompClientException(message, code, clientMessage, cause);
    }
}
