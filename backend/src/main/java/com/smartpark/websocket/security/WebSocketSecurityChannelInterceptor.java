package com.smartpark.websocket.security;

import com.smartpark.repository.ParkingLotRepository;
import com.smartpark.security.jwt.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inbound channel interceptor for STOMP WebSocket frames.
 * Enforces JWT authentication on STOMP CONNECT frames and destination-level
 * authorization on STOMP SUBSCRIBE frames.
 */
@Component
public class WebSocketSecurityChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSecurityChannelInterceptor.class);
    private static final Pattern PARKING_LOT_TOPIC_PATTERN = Pattern.compile("^/topic/parking-lots/(\\d+)$");

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ParkingLotRepository parkingLotRepository;

    public WebSocketSecurityChannelInterceptor(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            ParkingLotRepository parkingLotRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.parkingLotRepository = parkingLotRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticateConnection(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authenticateConnection(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getFirstNativeHeader("token");
        }

        if (authHeader == null || authHeader.isBlank()) {
            log.warn("STOMP CONNECT rejected: missing Authorization or token header");
            throw new BadCredentialsException("Missing Authorization header in STOMP CONNECT frame");
        }

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
        try {
            String email = jwtService.extractEmail(token);
            if (email == null || jwtService.isTokenExpired(token)) {
                throw new BadCredentialsException("Invalid or expired JWT token in STOMP CONNECT frame");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (!jwtService.validateToken(token, userDetails)) {
                throw new BadCredentialsException("Invalid JWT token for user " + email);
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            accessor.setUser(authentication);
            log.debug("STOMP connection authenticated successfully for user: {}", email);
        } catch (Exception ex) {
            log.warn("STOMP CONNECT authentication failed: {}", ex.getMessage());
            throw new BadCredentialsException("STOMP authentication failed: " + ex.getMessage());
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            log.warn("STOMP SUBSCRIBE rejected: unauthenticated user");
            throw new AccessDeniedException("Unauthenticated subscription is forbidden");
        }

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("Destination cannot be empty");
        }

        Matcher matcher = PARKING_LOT_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            log.warn("STOMP SUBSCRIBE rejected: destination {} does not match allowed pattern", destination);
            throw new AccessDeniedException("Unauthorized subscription destination: " + destination);
        }

        Long lotId;
        try {
            lotId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Invalid parking lot ID in destination: " + destination);
        }

        boolean lotExists = parkingLotRepository.existsById(lotId);
        if (!lotExists) {
            log.warn("STOMP SUBSCRIBE rejected: parking lot id={} does not exist", lotId);
            throw new AccessDeniedException("Parking lot id=" + lotId + " does not exist");
        }

        log.debug("STOMP subscription authorized for user {} to {}", user.getName(), destination);
    }
}
