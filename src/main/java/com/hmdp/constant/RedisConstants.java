package com.hmdp.constant;

public class RedisConstants {
   public static final String LOGIN_CODE_KEY = "login:code:";    //保存验证码的key
   public static final Long LOGIN_CODE_TTL = 2L;                 //设置验证码的有效期

   public static final String LOGIN_USER_KEY = "login:user:";    //保存redis中用户信息的key
   public static final Long LOGIN_USER_TTL =  30L;   //保存redis中用户信息的key


   public static final String SHOP_KEY = "cache:shop:";
   public static final Long SHOP_TTL =  30L;
   public static final Long SHOP_NULL_TTL =  3L;



   public static final String SHOPTYPE_KEY = "shoptype:";
   public static final Long SHOPTYPE_TTL = 1L;




}
