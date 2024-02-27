package com.hmdp.config;

import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.Redisson;
import org.redisson.client.RedisClientConfig;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissionConfig {
    @Bean
    public RedissonClient redisClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379").setPassword("xw20000826");
        return Redisson.create(config);
    }
}
