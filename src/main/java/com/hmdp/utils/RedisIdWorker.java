package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 2024-1-1当前时间戳
    private static final long BEGIN_TIMESTAMP = 1704067200L;

    // 序列号位数
    private static final int COUNT_BITS = 32;

    // id结构：时间戳+自增id；每天一个key，方便统计订单量
    public long nextId(String keyPrefix) {
        // 生成时间戳
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 生成序列号: 秒内计数器
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("inr:" + keyPrefix + ":" + date);
        return (timestamp << COUNT_BITS) | count;
    }
}
