package com.saproduction.navigator;

import static com.saproduction.navigator.NavigatorDtos.*;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;


@Component
class RealtimeTickets {

    private record Grant(
            UUID organization,
            Instant expiresAt
    ) {}

    private final Map<String, Grant> tickets = new ConcurrentHashMap<>();

    Ticket mint(UUID org) {
        var raw = Hashing.token();
        var expiry = Instant.now().plusSeconds(90);

        tickets.put(
                Hashing.sha256(raw),
                new Grant(org, expiry)
        );

        return new Ticket(
                raw,
                org,
                expiry
        );
    }

    UUID consume(String raw) {
        var grant = raw == null
                ? null
                : tickets.remove(Hashing.sha256(raw));

        if (grant == null || !grant.expiresAt().isAfter(Instant.now())) {
            throw new GatewayException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "INVALID_TICKET",
                    "Realtime ticket invalid or expired."
            );
        }

        return grant.organization();
    }
}


record NavigatorPrincipal(
        UUID organization
) implements Principal {

    @Override
    public String getName() {
        return organization.toString();
    }
}


@Configuration
@EnableWebSocketMessageBroker
class RealtimeConfig implements WebSocketMessageBrokerConfigurer {

    private final RealtimeTickets tickets;

    RealtimeConfig(RealtimeTickets tickets) {
        this.tickets = tickets;
    }

    @Bean
    public TaskScheduler navigatorBrokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(
                "navigator-ws-heartbeat-"
        );
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);

        return scheduler;
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {
        registry
                .addEndpoint("/ws/navigator")
                .setAllowedOriginPatterns(
                        "https://*",
                        "http://localhost:*"
                );
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        registry
                .enableSimpleBroker("/topic")
                .setTaskScheduler(
                        navigatorBrokerTaskScheduler()
                )
                .setHeartbeatValue(
                        new long[]{10_000, 10_000}
                );

        registry.setApplicationDestinationPrefixes(
                "/app"
        );
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(
                new ChannelInterceptor() {

                    @Override
                    public Message<?> preSend(
                            Message<?> message,
                            MessageChannel channel
                    ) {
                        var accessor =
                                MessageHeaderAccessor.getAccessor(
                                        message,
                                        StompHeaderAccessor.class
                                );

                        if (accessor == null) {
                            return message;
                        }

                        if (accessor.getCommand()
                                == StompCommand.CONNECT) {

                            var token =
                                    accessor.getFirstNativeHeader(
                                            "X-Navigator-Ticket"
                                    );

                            var organization =
                                    tickets.consume(token);

                            accessor.setUser(
                                    new NavigatorPrincipal(
                                            organization
                                    )
                            );
                        }

                        if (accessor.getCommand()
                                == StompCommand.SUBSCRIBE) {

                            if (!(accessor.getUser()
                                    instanceof NavigatorPrincipal principal)) {

                                throw realtimeForbidden();
                            }

                            String expectedDestination =
                                    "/topic/organizations/"
                                            + principal.organization()
                                            + "/locations";

                            if (!Objects.equals(
                                    accessor.getDestination(),
                                    expectedDestination
                            )) {
                                throw realtimeForbidden();
                            }
                        }

                        return message;
                    }
                }
        );
    }

    private GatewayException realtimeForbidden() {
        return new GatewayException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "REALTIME_FORBIDDEN",
                "Realtime subscription denied."
        );
    }
}


@Component
class RealtimePublisher {

    private final SimpMessagingTemplate messages;
    private final GatewayAuth auth;

    RealtimePublisher(
            SimpMessagingTemplate messages,
            GatewayAuth auth
    ) {
        this.messages = messages;
        this.auth = auth;
    }

    @EventListener
    void publish(Event event) {
        messages.convertAndSend(
                "/topic/organizations/"
                        + auth.publicId()
                        + "/locations",
                event
        );
    }
}