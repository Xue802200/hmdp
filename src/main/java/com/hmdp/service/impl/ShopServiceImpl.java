package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.constant.RedisConstants;
import com.hmdp.constant.SystemConstants;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商品,添加相关缓存
     * @param id
     * @return
     */
    @Override
    public Object queryById(Long id){
        //1.先根据商品id去缓存当中查询
        String shopJSON = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_KEY + id);  //得到shop对象的序列化数据

        //2.如果缓存中存在,则直接将这个信息返回
        if(StrUtil.isNotBlank(shopJSON)){
            return JSON.parseObject(shopJSON,Shop.class);
        }

        //3.缓存当中不存在,则去数据库当中查找
        Shop shop = getById(id);

        //4.数据库当中的数据不存在,直接返回错误
        if( shop == null){
            return SystemConstants.SHOP_NOT_EXIST;
        }

        //5.数据库当中的数据存在,则将这个对象添加到缓存当中
        //5.1将对象序列化为JSON类型的数据
        String shopMysqlJSON = JSON.toJSONString(shop);

        //5.2将序列化后的数据添加到redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_KEY + id ,shopMysqlJSON);

        //6.返回当前商品信息
        return shop;
    }
}
