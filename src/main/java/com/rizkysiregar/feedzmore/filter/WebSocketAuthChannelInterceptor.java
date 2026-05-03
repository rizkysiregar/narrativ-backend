package com.rizkysiregar.feedzmore.filter;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.service.JWTService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JWTService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    // Reuse centralized logic
                    Authentication auth = jwtService.validateAndGetAuthentication(token);
                    accessor.setUser(auth);
                } catch (Exception e) {
                    log.error("WebSocket auth failed: {}", e.getMessage());
                    // Throwing exception here prevents the CONNECT from succeeding
//                    throw new MessageDeliveryException(message, "Unauthorized: " + e.getMessage());
                    throw new BusinessException(GlobalMessage.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
                }
            } else {
//                throw new MessageDeliveryException(message, "Missing Authorization header");
                log.error("Missing Authorization header");
                throw new BusinessException(GlobalMessage.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
        return message;
    }
}
