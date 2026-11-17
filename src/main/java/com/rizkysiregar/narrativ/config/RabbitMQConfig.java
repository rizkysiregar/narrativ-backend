package com.rizkysiregar.narrativ.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RabbitMQConfig {

    // --- Exchanges ---
    public static final String POST_EXCHANGE = "post.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "dlx.exchange";

    // --- Queues ---
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String EMAIL_QUEUE = "email.queue";
    public static final String COMMENT_REPLY_QUEUE = "comment.reply.queue";
    public static final String POST_LIKED_QUEUE = "post.liked.queue";
    public static final String VIDEO_THUMBNAIL_REQUEST_QUEUE = "video.thumbnail.request.queue";
    public static final String VIDEO_THUMBNAIL_RESULT_QUEUE = "video.thumbnail.result.queue";

    // --- Dead letter queues ---
    public static final String NOTIFICATION_DLQ = "notification.dlq";
    public static final String EMAIL_DLQ = "email.dlq";
    public static final String COMMENT_REPLY_DLQ = "comment.reply.dlq";
    public static final String POST_LIKED_DLQ = "post.liked.dlq";
    public static final String VIDEO_THUMBNAIL_REQUEST_DLQ = "video.thumbnail.request.dlq";
    public static final String VIDEO_THUMBNAIL_RESULT_DLQ = "video.thumbnail.result.dlq";

    // --- Routing keys ---
    public static final String POST_CREATED_ROUTING_KEY = "post.created";
    public static final String COMMENT_REPLIED_ROUTING_KEY = "comment.replied";
    public static final String POST_LIKED_ROUTING_KEY = "post.liked";
    public static final String VIDEO_THUMBNAIL_REQUESTED_ROUTING_KEY = "video.thumbnail.requested";
    public static final String VIDEO_THUMBNAIL_GENERATED_ROUTING_KEY = "video.thumbnail.generated";

    // --- Consumer identities (used to build idempotency keys) ---
    public static final String NOTIFICATION_CONSUMER = "notification";
    public static final String EMAIL_CONSUMER = "email";
    public static final String COMMENT_REPLY_CONSUMER = "comment-reply";
    public static final String POST_LIKE_CONSUMER = "post-like";

    public static final int LISTENER_PREFETCH_COUNT = 10;
    public static final int LISTENER_MAX_RETRIES = 3;

    @Bean
    public TopicExchange postExchange() {
        return new TopicExchange(POST_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // Return unroutable messages to the publisher instead of silently dropping them.
        template.setMandatory(true);
        template.setObservationEnabled(true);
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (ack) {
                log.debug("RABBITMQ PUBLISH CONFIRMED id={}",
                        correlation != null ? correlation.getId() : null);
            } else {
                log.error("RABBITMQ PUBLISH NACKED id={} cause={}",
                        correlation != null ? correlation.getId() : null, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "RABBITMQ MESSAGE RETURNED exchange={} routingKey={} replyCode={} replyText={}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText()));
        return template;
    }

    // --- Notification ---
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(postExchange())
                .with(POST_CREATED_ROUTING_KEY);
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    @Bean
    public Binding notificationDlqBinding() {
        return BindingBuilder
                .bind(notificationDlq())
                .to(deadLetterExchange())
                .with(NOTIFICATION_DLQ);
    }

    // --- Email ---
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", EMAIL_DLQ)
                .build();
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder
                .bind(emailQueue())
                .to(postExchange())
                .with(POST_CREATED_ROUTING_KEY);
    }

    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(EMAIL_DLQ).build();
    }

    @Bean
    public Binding emailDlqBinding() {
        return BindingBuilder
                .bind(emailDlq())
                .to(deadLetterExchange())
                .with(EMAIL_DLQ);
    }

    // --- Post liked (push to the post owner) ---
    @Bean
    public Queue postLikedQueue() {
        return QueueBuilder.durable(POST_LIKED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", POST_LIKED_DLQ)
                .build();
    }

    @Bean
    public Binding postLikedBinding() {
        return BindingBuilder
                .bind(postLikedQueue())
                .to(postExchange())
                .with(POST_LIKED_ROUTING_KEY);
    }

    @Bean
    public Queue postLikedDlq() {
        return QueueBuilder.durable(POST_LIKED_DLQ).build();
    }

    @Bean
    public Binding postLikedDlqBinding() {
        return BindingBuilder
                .bind(postLikedDlq())
                .to(deadLetterExchange())
                .with(POST_LIKED_DLQ);
    }

    // --- Comment on a post (push to the post owner and to the owner of a comment that was answered) ---
    @Bean
    public Queue commentReplyQueue() {
        return QueueBuilder.durable(COMMENT_REPLY_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", COMMENT_REPLY_DLQ)
                .build();
    }

    @Bean
    public Binding commentReplyBinding() {
        return BindingBuilder
                .bind(commentReplyQueue())
                .to(postExchange())
                .with(COMMENT_REPLIED_ROUTING_KEY);
    }

    @Bean
    public Queue commentReplyDlq() {
        return QueueBuilder.durable(COMMENT_REPLY_DLQ).build();
    }

    @Bean
    public Binding commentReplyDlqBinding() {
        return BindingBuilder
                .bind(commentReplyDlq())
                .to(deadLetterExchange())
                .with(COMMENT_REPLY_DLQ);
    }

    // --- Video Thumbnail (media worker) ---
    // The worker consumes the request queue and publishes to POST_EXCHANGE with the
    // generated routing key. Topology is declared here only; the worker never declares it.
    @Bean
    public Queue videoThumbnailRequestQueue() {
        return QueueBuilder.durable(VIDEO_THUMBNAIL_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", VIDEO_THUMBNAIL_REQUEST_DLQ)
                .build();
    }

    @Bean
    public Binding videoThumbnailRequestBinding() {
        return BindingBuilder
                .bind(videoThumbnailRequestQueue())
                .to(postExchange())
                .with(VIDEO_THUMBNAIL_REQUESTED_ROUTING_KEY);
    }

    @Bean
    public Queue videoThumbnailRequestDlq() {
        return QueueBuilder.durable(VIDEO_THUMBNAIL_REQUEST_DLQ).build();
    }

    @Bean
    public Binding videoThumbnailRequestDlqBinding() {
        return BindingBuilder
                .bind(videoThumbnailRequestDlq())
                .to(deadLetterExchange())
                .with(VIDEO_THUMBNAIL_REQUEST_DLQ);
    }

    @Bean
    public Queue videoThumbnailResultQueue() {
        return QueueBuilder.durable(VIDEO_THUMBNAIL_RESULT_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", VIDEO_THUMBNAIL_RESULT_DLQ)
                .build();
    }

    @Bean
    public Binding videoThumbnailResultBinding() {
        return BindingBuilder
                .bind(videoThumbnailResultQueue())
                .to(postExchange())
                .with(VIDEO_THUMBNAIL_GENERATED_ROUTING_KEY);
    }

    @Bean
    public Queue videoThumbnailResultDlq() {
        return QueueBuilder.durable(VIDEO_THUMBNAIL_RESULT_DLQ).build();
    }

    @Bean
    public Binding videoThumbnailResultDlqBinding() {
        return BindingBuilder
                .bind(videoThumbnailResultDlq())
                .to(deadLetterExchange())
                .with(VIDEO_THUMBNAIL_RESULT_DLQ);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setPrefetchCount(LISTENER_PREFETCH_COUNT);
        // Propagate the trace context from the publisher into the consumer span.
        factory.setObservationEnabled(true);
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .maxRetries(LISTENER_MAX_RETRIES)
                        .backOffOptions(1000, 2.0, 5000)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build()
        );

        return factory;
    }


}
