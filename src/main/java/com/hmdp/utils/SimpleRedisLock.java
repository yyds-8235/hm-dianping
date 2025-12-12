package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import io.lettuce.core.RedisClient;
import lombok.AllArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * SimpleRedisLock
 *
 * @Description
 * @Author hanchenyang
 * @Date 2025/12/2 16:42
 */
@AllArgsConstructor
public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KET_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(Long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String key = KET_PREFIX + name;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {

        Long execute = stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KET_PREFIX + name), ID_PREFIX + Thread.currentThread().getId());
        if (execute == 1) {
            System.out.println("释放锁成功");
        }

// 非原子性       String threadId = ID_PREFIX + Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KET_PREFIX + name);
//        if (threadId.equals(id)) {
//            stringRedisTemplate.delete(KET_PREFIX + name);
//        }


    }
}
