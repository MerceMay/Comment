package com.mercemay.comment.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @param name 业务名称
 */
public record SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) implements ILock {
    // UUID：用来标识不同的客户端
    private static final String ID_PREFIX = UUID.randomUUID().toString();
    // 分布式锁的前缀
    private static final String KEY_PREFIX = "lock:distributed:";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT; // Long表示返回值类型

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua")); // 加载Lua脚本
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程的标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 尝试获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}
