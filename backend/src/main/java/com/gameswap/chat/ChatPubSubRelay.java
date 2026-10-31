package com.gameswap.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameswap.chat.ChatDtos.ChatMessageDto;
import com.gameswap.chat.ChatDtos.RedisChatEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class ChatPubSubRelay implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ChatPubSubRelay.class);
    public static final String CHAT_CHANNEL = "gameswap:chat:events";

    private final StringRedisTemplate redisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private RedisMessageListenerContainer listenerContainer;

    public ChatPubSubRelay(
        StringRedisTemplate redisTemplate,
        RedisConnectionFactory connectionFactory,
        SimpMessagingTemplate messagingTemplate,
        ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startListener() {
        try {
            listenerContainer = new RedisMessageListenerContainer();
            listenerContainer.setConnectionFactory(connectionFactory);
            listenerContainer.addMessageListener(this, new ChannelTopic(CHAT_CHANNEL));
            listenerContainer.afterPropertiesSet();
            listenerContainer.start();
        } catch (Exception e) {
            log.warn("Redis Pub/Sub listener could not start (falling back to single-node WebSocket broadcast): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stopListener() {
        if (listenerContainer != null) {
            try {
                listenerContainer.stop();
            } catch (Exception ignored) {
            }
        }
    }

    public void publishChatMessage(UUID threadId, ChatMessageDto messageDto) {
        try {
            String payload = objectMapper.writeValueAsString(new RedisChatEvent(threadId, messageDto));
            if (listenerContainer != null && listenerContainer.isRunning()) {
                redisTemplate.convertAndSend(CHAT_CHANNEL, payload);
                return;
            }
        } catch (Exception e) {
            log.debug("Redis publish failed, broadcasting directly to local STOMP broker: {}", e.getMessage());
        }
        messagingTemplate.convertAndSend("/topic/threads." + threadId, messageDto);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            RedisChatEvent event = objectMapper.readValue(json, RedisChatEvent.class);
            messagingTemplate.convertAndSend("/topic/threads." + event.threadId(), event.message());
        } catch (Exception e) {
            log.warn("Failed to process Redis chat pub/sub message: {}", e.getMessage());
        }
    }
}

