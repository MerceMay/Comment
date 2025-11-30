package com.mercemay.comment.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.mercemay.comment.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 保存到 Redis （普通缓存）
     *
     * @param key      键
     * @param value    值
     * @param time     时间
     * @param timeUnit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 保存到 Redis （逻辑过期）
     *
     * @param key      键
     * @param value    值
     * @param time     时间
     * @param timeUnit 时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotEmpty(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 命中，但是是""，说明是缓存穿透
        if (json != null) {
            return null;
        }
        R r = dbFallback.apply(id);
        if (r == null) {
            // 查询数据库不存在，写入空值解决缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", time, timeUnit);
            return null;
        }
        // 存在，写入redis
        this.set(key, r, time, timeUnit);
        return r;
    }

    /**
     * 缓存击穿
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String lockKey = LOCK_SHOP_KEY + id;

        while (true) {
            // 1. 从 redis 查询缓存
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            if (json != null) {
                return null;
            }

            // 2. 尝试获取锁
            R r = null;
            try {
                boolean isLock = tryLock(lockKey);
                if (!isLock) {
                    // 获取锁失败，休眠
                    Thread.sleep(50);
                    continue; // 重新进入循环
                }

                // 3. 获取锁成功，Double Check (再次查询缓存，防止重复查库)
                json = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(json)) {
                    return JSONUtil.toBean(json, type);
                }
                if (json != null) {
                    return null; // 依然是空值缓存
                }

                // 4. 查询数据库
                r = dbFallback.apply(id);
                if (r == null) {
                    stringRedisTemplate.opsForValue().set(key, "", time, timeUnit);
                    return null;
                }
                this.set(key, r, time, timeUnit);
                return r;

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                // 5. 释放锁
                unlock(lockKey);
            }
        }
    }


    // 线程池，用于缓存重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // 命中，先反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        // 已过期，需要缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        if (isLock) {
            // 双重检查
            String json2 = stringRedisTemplate.opsForValue().get(key);
            RedisData redisData2 = JSONUtil.toBean(json2, RedisData.class);
            if (redisData2.getExpireTime().isAfter(LocalDateTime.now())) {
                unlock(lockKey); // 记得释放锁
                return JSONUtil.toBean((JSONObject) redisData2.getData(), type);
            }

            // 防止提交任务失败导致锁死
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 查询数据库
                        R r1 = dbFallback.apply(id);
                        // 写入 Redis（逻辑过期）
                        this.setWithLogicalExpire(key, r1, time, timeUnit);
                    } catch (Exception e) {
                        log.error("Cache rebuild failed", e);
                    } finally {
                        unlock(lockKey);
                    }
                });
            } catch (Exception e) {
                // 如果提交线程池失败（例如线程池满拒绝），必须在主线程释放锁
                log.error("Failed to submit rebuild task", e);
                unlock(lockKey);
            }
        }
        return r;
    }


    /**
     * 利用redis的setnx方法来表示获取锁，如果redis没有这个key，则插入成功，返回1，如果已经存在这个key，则插入失败，返回0。
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 删除先前使用setnx方法设置的 lockKey
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
