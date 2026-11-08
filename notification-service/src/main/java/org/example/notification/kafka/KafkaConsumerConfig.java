package org.example.notification.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    /**
     * Если письмо не ушло (SMTP временно недоступен) - 3 повтора с паузой 2 сек,
     * затем сообщение пропускается, чтобы не блокировать остальные.
     * Ошибки десериализации не повторяются.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(2000L, 3L));
    }
}
