package com.hmdp.constant;

public class RedisConstants {
   public static final String LOGIN_CODE_KEY = "login:code:";    //保存验证码的key
   public static final Long LOGIN_CODE_TTL = 2L;                 //设置验证码的有效期

   public static final String LOGIN_USER_KEY = "login:user:";    //保存redis中用户信息的key
   public static final Long LOGIN_USER_TTL =  30L;   //保存redis中用户信息的key


   public static final String CACHE_SHOP_KEY = "cache:shop:";
   public static final Long CACHE_SHOP_TTL =  30L;
   public static final Long CACHE_NULL_TTL =  3L;   //解决缓存穿透,写入缓存的null值有效时长

   public static final String LOCK_SHOP_KEY = "lock:shop:";
   public static final Long LOCK_SHOP_TTL =  10L;


   public static final String CACHE_SHOPTYPE_KEY = "shoptype";
   public static final Long CACHE_SHOPTYPE_TTL = 1L;


   public static final String LIKE_BLOG_KEY = "like:blog:";




}
