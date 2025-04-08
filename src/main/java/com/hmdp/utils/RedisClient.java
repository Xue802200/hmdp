package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.hmdp.constant.RedisConstants;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class RedisClient {
    private final StringRedisTemplate stringRedisTemplate;
    //自定义线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public RedisClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将任意java对象存储到redis当中并设置过期时间
     * @param key       存入redis的key名称
     * @param value     存入的值
     * @param ttl       设置过期的时间
     * @param timeUnit  时间单位
     */
    public void set(String key,Object value, Long ttl, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(value), ttl, timeUnit);
    }

    /**
     * 将任意java对象存入到redis中,并且可以设置逻辑过期时间,解决缓存击穿问题
     * @param key       存入redis的key名称
     * @param value     存入的值
     * @param ttl       设置过期的时间
     * @param timeUnit  时间单位
     */
    public void setWithLogicalExpire(String key,Object value, Long ttl, TimeUnit timeUnit){
        //封装value对象
        RedisData data = new RedisData();
        data.setData(value);
        data.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(ttl)));

        //将对象序列化为json数据
        String jsonString = JSON.toJSONString(data);
        stringRedisTemplate.opsForValue().set(key, jsonString);
    }

    /**
     * 根据指定的key进行查询,用缓存空值的方式解决缓存穿透问题
     * @param keyPrefix    key的前缀
     * @param id           要查询的id
     * @param type         返回值的类型
     * @return             要查询的对应值
     * @param <R>          返回值类型
     * @param <T>          id的类型
     */
    public <R,T> R queryWithPassThrough(String keyPrefix, T id, Class<R> type, Function<T,R> dbFallBack,Long ttl, TimeUnit timeUnit){
        //1.先去redis中查找对应的缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.如果缓存命中,直接将查询得到的值返回即可
        if(StrUtil.isNotBlank(json)){
            return JSON.parseObject(json,type);
        }

        //3.如果查询得到的值是"",那么直接返回null,这是为了解决缓存穿透的
        if("".equals(json)){
            return null;
        }

        //4.如果缓存未命中,需要根据id去数据库中查找
        R result = dbFallBack.apply(id);

        //5.如果数据库中的数据不存在,则将null值写入redis当中
        if(result == null){
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.SECONDS);
            return null;
        }

        //6.如果数据库中的数据存在,那么将这个数据写入缓存当中
        this.set(key,result,ttl,timeUnit);

        return result;
    }

    /*
    利用逻辑过期解决缓存击穿的问题
     */
    public <R,T> R queryWithLogicalExpire(String keyPrefix, T id, Class<R> type, Function<T,R> dbFallBack,Long ttl, TimeUnit timeUnit){
        //1.在缓存中查找数据
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.如果缓存未命中,返回null
        if(StrUtil.isBlank(json)){
            return null;
        }

        //3.如果缓存命中,判断逻辑时间是否过期
        RedisData redisData = JSON.parseObject(json, RedisData.class); //先转为RedisData对象
        LocalDateTime expireTime = redisData.getExpireTime();
        R result = JSON.parseObject(redisData.getData().toString(), type);

        //4.如果逻辑没有超时,则直接将旧数据返回
        if(expireTime.isAfter(LocalDateTime.now())){
            return result;
        }

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        //5.如果逻辑没有过期,则去更新逻辑失效时间,首先需要获取互斥锁
        if(lock(lockKey)){
            //6.获取到锁,进行缓存重建
            R apply = dbFallBack.apply(id);
            try {
                //6.2缓存中没有数据再执行缓存重建的操作,开启独立线程,在独立线程中执行缓存重建的操作
                CACHE_REBUILD_EXECUTOR.submit(()->{
                   this.setWithLogicalExpire(key,apply,ttl,timeUnit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //6.3释放锁
                unlockShop(key);
            }
        }

        //7.直接返回旧数据
        return result;
    }

    /*
   模拟上互斥锁的过程,拿到互斥锁则返回true,否则返回false
    */
    public boolean lock(String key){
        //判断是否能够插入数据,如果为true,则说明已经拿到锁了可以进行操作,false则说明未拿到锁
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);

        //避免拆箱所导致的空指针问题
        return BooleanUtil.isTrue(result);
    }

    /*
    模拟释放锁的过程,即将原来的key给删除即可
     */
    public void unlockShop(String key){
        stringRedisTemplate.delete(key);
    }


}
