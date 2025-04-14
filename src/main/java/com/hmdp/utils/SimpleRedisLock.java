package com.hmdp.utils;

import cn.hutool.core.lang.ResourceClassLoader;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private final String name;                        //存放分布式锁的key名称
    private final StringRedisTemplate stringredisTemplate;


    private static final String key_prefix = "lock:";
    private static final String id_prefix = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    static{
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        REDIS_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.stringredisTemplate = redisTemplate;
    }

    /**
     * 在redis中获取一个分布式锁
     * key:lock:+secKill: + userId  value:uuid+threadId
     * @param ttl  锁的过期时间
     */
    @Override
    public boolean tryLock(Long ttl) {
        //用uuid+线程id作为value值
        String threadId =id_prefix + Thread.currentThread().getId();

        //判断是否能上锁
        //分布式锁在redis中的key前缀
        Boolean result = stringredisTemplate.opsForValue().setIfAbsent(key_prefix + name,threadId, ttl, TimeUnit.SECONDS);

        //返回结果
        return BooleanUtil.isTrue(result);
    }

    @Override
    public void unLock() {
        stringredisTemplate.execute(REDIS_SCRIPT,
                Collections.singletonList(key_prefix + name),
                Collections.singletonList(id_prefix + Thread.currentThread().getId())
                );
    }


    /**
     * 手动删除redis的锁,会造成redis的原子性问题
     */
//    @Override
//    public void unLock() {
//        //1.获取缓存中锁存储的值
//        String value = stringredisTemplate.opsForValue().get(key_prefix + name);
//
//        //获取当前的值
//        String value1 = id_prefix + Thread.currentThread().getId();
//
//        //相同才能执行释放锁的操作
//        if(value != null && value.equals(value1)) {
//            //释放锁
//            stringredisTemplate.delete(key_prefix + name);
//        }
//    }
}
