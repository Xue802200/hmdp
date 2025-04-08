package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.constant.RedisConstants;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 查询商家类型
     * @return
     */
    @Override
    public Result queryList() {
        //1.先去缓存当中查找是否有数据
        String shopTypeJSON = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOPTYPE_KEY);

        //2.如果有数据,则直接返回
        if (shopTypeJSON != null && !shopTypeJSON.isEmpty()) {
            try {
                // 2. 使用 FastJSON 解析 JSON 数组 → List<ShopType>
                List<ShopType> shopTypeList = JSON.parseObject(
                        shopTypeJSON,
                        new TypeReference<List<ShopType>>() {}
                );
                return Result.ok(shopTypeList);
            } catch (Exception e) {
                return Result.fail("系统错误");
            }
        }

        //3.如果没有数据,则去数据库中查找
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        //4.如果数据库中的数据为空,则直接抛出错误
        if(shopTypeList == null || shopTypeList.isEmpty()){
            return Result.fail(SystemConstants.SHOPTYPE_NOT_EXIST);
        }

        //5.不为空,则将对象序列化后添加到redis当中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOPTYPE_KEY, JSON.toJSONString(shopTypeList));

        //6.为了避免缓存穿透的问题,设置缓存有效期为一天
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOPTYPE_KEY,RedisConstants.CACHE_SHOPTYPE_TTL,TimeUnit.DAYS);

        //6.返回当前对象
        return Result.ok(shopTypeList);
    }

}
