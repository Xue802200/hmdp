package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.constant.RedisConstants;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

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
    public Result queryById(Long id){
        //1.先根据商品id去缓存当中查询
        String shopJSON = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_KEY + id);  //得到shop对象的序列化数据

        //2.1 如果缓存中存在且为非空,则直接将这个信息返回
        if(StrUtil.isNotBlank(shopJSON)){
            //将对象序列化后返回给客户端
            return Result.ok(JSON.parseObject(shopJSON,Shop.class));
        }

        //执行到这就说明shopJSON肯定不为空,所以此时只要shopJSON也不为null就符合""的情况
        //2.2 如果缓存当中存在并且为""的,那么需要返回错误
        if("".equals(shopJSON)){
            return Result.fail(SystemConstants.SHOP_NOT_EXIST);
        }

        //3.缓存当中不存在,则去数据库当中查找
        Shop shop = getById(id);

        //4.数据库当中的数据不存在,直接返回错误 ->past
        //4.数据库当中的数据不存在,将一个空值存入到redis当中
        if( shop == null){
            stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_KEY + id ,"", RedisConstants.SHOP_NULL_TTL , TimeUnit.MINUTES);
            return Result.fail(SystemConstants.SHOP_NOT_EXIST);
        }

        //5.数据库当中的数据存在,则将这个对象添加到缓存当中
        //5.1将对象序列化为JSON类型的数据
        String shopMysqlJSON = JSON.toJSONString(shop);

        //5.2将序列化后的数据添加到redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_KEY + id ,shopMysqlJSON , RedisConstants.SHOP_TTL , TimeUnit.MINUTES);

        //6.返回当前商品信息
        return Result.ok(shop);
    }

    /**
     *
     * @param shop  更新后的商铺信息
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        if(shop.getId() == null){
            return Result.fail(SystemConstants.SHOPID_NOT_EXIST);
        }
        //1.先更新数据库
        updateById(shop);

        //2.在删除缓存
        stringRedisTemplate.delete(RedisConstants.SHOP_KEY + shop.getId());

        return Result.ok(shop);
    }
}
