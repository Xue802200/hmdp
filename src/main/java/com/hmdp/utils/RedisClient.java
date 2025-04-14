package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
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
     * 将任意java对象存入到redis中,并且可以设置逻辑过期时间
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

    /**
     * 利用逻辑过期解决缓存击穿的问题
     * @param keyPrefix   缓存的前缀名
     * @param id          查找物品的id
     * @param type        返回值的类型
     * @param ttl         逻辑过期时间
     * @param timeUnit    过期时间单位
     * @return            查找商品的信息
     */
    public <R,T> R queryWithLogicalExpire(String keyPrefix, T id,Class<R> type,Function<T,R> dbFallBack,Long ttl, TimeUnit timeUnit){
        //1.先根据key的全名称在缓存中查找
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.缓存中不存在,直接返回空
        if(StrUtil.isBlank(json)){
            return null;
        }

        //3.缓存中存在,进行反序列化
        RedisData redisData = JSON.parseObject(json, RedisData.class);
        Object data =  redisData.getData();
        R result = JSON.parseObject(json, type);                           //存储的object属性
        LocalDateTime expireTime = redisData.getExpireTime();              //逻辑过期时间

        //4.如果没有逻辑过期,直接返回
        if(LocalDateTime.now().isAfter(expireTime)){
            return result;
        }

        //5.如果逻辑过期了,则进行更新过期时间的操作
        //获取互斥锁
        boolean lockResult = tryLock(key);
        if(lockResult){
            try {
                //获取锁成功,新建线程去执行更新的操作
                CACHE_REBUILD_EXECUTOR.submit(() ->{
                    R apply = dbFallBack.apply(id);
                    setWithLogicalExpire(key,apply,ttl,timeUnit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //释放锁
                unLock(key);
            }
        }

        //返回旧数据
        return result;
    }

    /**
     * 获取互斥锁
     * @param key   key的名称
     * @return      是否获取到
     */
    private boolean tryLock(String key){
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(result);
    }

    /**
     * 释放锁
     * @param key   key的名称
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
