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

    /**
     * 查询商家类型
     * @return
     */
    @Override
    public Result queryList() {
        //1.先去缓存当中查找
        String shoptypeJSON = stringRedisTemplate.opsForValue().get(RedisConstants.SHOPTYPE_KEY);

        //2.如果缓存当中有数据,则直接返回
        if(StrUtil.isNotBlank(shoptypeJSON)){
            //将字符串反序列化为对应的集合类型
            List<ShopType>  shopTypeList = JSON.parseArray(shoptypeJSON,ShopType.class);
            return Result.ok(shopTypeList);
        }

        //3.如果缓存当中没有数据,去数据库中查询
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        //4.数据库中没有数据,则返回错误信息
        if ( shopTypeList == null || shopTypeList.isEmpty()){
            return Result.fail(SystemConstants.SHOPTYPE_NOT_EXIST);
        }

        //5.数据库中有数据,则将当前数据添加到缓存当中  序列化为JSON并存入Redis List
        try {
            // 5.1 将List<ShopType>转为JSON字符串列表
            List<String> jsonList = shopTypeList.stream()
                    .map(shopType -> JSON.toJSONString(shopType))
                    .collect(Collectors.toList());

            // 5.2 批量插入到Redis List（使用rightPushAll）
            stringRedisTemplate.opsForList().rightPushAll(
                    RedisConstants.SHOPTYPE_KEY,
                    jsonList  // 直接传入List<String>
            );
        } catch (Exception e) {
            log.error("缓存店铺类型失败", e);
        }

        //6.返回当前集合对象
        return Result.ok(shopTypeList);
    }
}
