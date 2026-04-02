package com.example.spring_stream_backend.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "video.exchange";
    public static final String QUEUE = "video.processing.queue";
    public static final String ROUTING_KEY = "video.uploaded";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue queue() {
        return new Queue(QUEUE, true);
    }
    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(ROUTING_KEY);
    }
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }
}
