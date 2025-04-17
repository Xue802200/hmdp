package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/*
基于redis实现的全局id生成器
 */
@Component
public class RedisIdWorker {
    /*
        stringRedisTemplate的工具类
         */
    private StringRedisTemplate stringRedisTemplate;

    /*
    起始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private static final int BIT_SHIFT = 32;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
    根据前缀来创建全局id   icr:keyPrefix:
    */
    public long createId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowEpochSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1生成当前时间
        LocalDate nowDay = LocalDate.now();
        //2.2自定义日期格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd");
        //2.3格式化为指定形式
        String format = formatter.format(nowDay);

        //number用来表示是当天第多少个订单
        long number = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + format);


        //3.拼接返回
        return timestamp << BIT_SHIFT | number;
    }


//    public static void main(String[] args) {
//        LocalDateTime localDateTime = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
//        long l = localDateTime.toEpochSecond(ZoneOffset.UTC);
//        System.out.println("l="+l);
//    }
}
