package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
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
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    //自定义线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 查询商品,添加相关缓存
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id){
//        //解决缓存穿透的方案
//        Shop shop = getShopWithCachePenetration(id);

//        //解决缓存击穿的方案
//        Shop shop = getShopWithCacheBreakDown(id);

        Shop shop = getShopWithCacheBreakDown2(id);

        if(shop == null){
            return Result.fail(SystemConstants.SHOP_NOT_EXIST);
        }
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

    /*
    模拟上互斥锁的过程,拿到互斥锁则返回true,否则返回false
     */
    public boolean lockShop(Long id){
        //判断是否能够插入数据,如果为true,则说明已经拿到锁了可以进行操作,false则说明未拿到锁
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);

        //避免拆箱所导致的空指针问题
        return BooleanUtil.isTrue(result);
    }

    /*
    模拟释放锁的过程,即将原来的key给删除即可
     */
    public void unlockShop(Long id){
        stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY + id);
    }


//    /*
//    解决缓存穿透的代码
//     */
//    public Shop getShopWithCachePenetration(Long id){
//        //1.先根据商品id去缓存当中查询
//        String shopJSON = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_KEY + id);  //得到shop对象的序列化数据
//
//        //2.1 如果缓存中存在且为非空,则直接将这个信息返回
//        if(StrUtil.isNotBlank(shopJSON)){
//            //将对象序列化后返回给客户端
//            return JSON.parseObject(shopJSON,Shop.class);
//        }
//
//        //执行到这就说明shopJSON肯定不为空,所以此时只要shopJSON也不为null就符合""的情况
//        //2.2 如果缓存当中存在并且为""的,那么需要返回错误
//        if("".equals(shopJSON)){
//            return null;
//        }
//
//        //3.缓存当中不存在,则去数据库当中查找
//        Shop shop = getById(id);
//
//        //4.数据库当中的数据不存在,直接返回错误 ->past
//        //4.数据库当中的数据不存在,将一个空值存入到redis当中
//        if( shop == null){
//            stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_KEY + id ,"", RedisConstants.SHOP_NULL_TTL , TimeUnit.MINUTES);
//            return null;
//        }
//
//        //5.数据库当中的数据存在,则将这个对象添加到缓存当中
//        //5.1将对象序列化为JSON类型的数据
//        String shopMysqlJSON = JSON.toJSONString(shop);
//
//        //5.2将序列化后的数据添加到redis中
//        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_KEY + id ,shopMysqlJSON , RedisConstants.SHOP_TTL , TimeUnit.MINUTES);
//
//        //6.返回当前商品信息
//        return shop;
//    }

    /*
    解决缓存击穿的代码实现--使用互斥锁实现
     */
    public Shop getShopWithCacheBreakDown(Long id){
        //1.先根据商品id去缓存当中查询
        String shopJSON = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_KEY + id);  //得到shop对象的序列化数据

        //2.1 如果缓存中存在且为非空,则直接将这个信息返回
        if(StrUtil.isNotBlank(shopJSON)){
            //将对象序列化后返回给客户端
            return JSON.parseObject(shopJSON,Shop.class);
        }

        //执行到这就说明shopJSON肯定不为空,所以此时只要shopJSON也不为null就符合""的情况
        //2.2 如果缓存当中存在并且为""的,那么需要返回错误
        if("".equals(shopJSON)){
            return null;
        }
        Shop shop = null;

        try {
            //3.缓存当中不存在,判断是否拿到了互斥锁
            if(!lockShop(id)){
                //4.未拿到互斥锁,则让当前线程休眠
                Thread.sleep(200);

                //休眠后重新执行当前逻辑
                return getShopWithCacheBreakDown(id);
            }

            //5.拿到互斥锁了之后,去数据库当中查找数据
            shop = getById(id);
            Thread.sleep(200); //模拟高并发造成的问题
            //6.数据库当中的数据不存在,将一个空值存入到redis当中
            if( shop == null){
                stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_KEY + id ,"", RedisConstants.SHOP_NULL_TTL , TimeUnit.MINUTES);
                return null;
            }

            //5.数据库当中的数据存在,则将这个对象添加到缓存当中
            //5.1将对象序列化为JSON类型的数据
            String shopMysqlJSON = JSON.toJSONString(shop);

            //5.2将序列化后的数据添加到redis中
            stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_KEY + id ,shopMysqlJSON , RedisConstants.SHOP_TTL , TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unlockShop(id);
        }


        //6.返回当前商品信息
        return shop;
    }

    /**
     * 解决缓存击穿的代码实现--通过逻辑过期实现
     * @param id  要查询商品的id
     * @return   查询到的商品信息
     */
    public Shop getShopWithCacheBreakDown2(Long id){
        //1.先从缓存中查找数据
        String shopJSON = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_KEY + id);

        //判断缓存是否命中
        if(StrUtil.isBlank(shopJSON)){
            //2.缓存未命中,直接返回null
            return null;
        }

        //3.缓存命中了,需要对逻辑时间进行判断
        RedisData redisData = JSON.parseObject(shopJSON, RedisData.class); //先饭序列换为RedisData对象对于其中的data属性还要进行反序列化
        Shop shop = JSON.parseObject(redisData.getData().toString(), Shop.class); //得到data属性
        LocalDateTime expireTime = redisData.getExpireTime(); //获取到逻辑属性

        //4.如果逻辑时间没有超时,那么可以直接将当前数据返回
        if(LocalDateTime.now().isBefore(expireTime)){
            return shop;
        }

        //5.如果逻辑时间超时了,首先需要去获取互斥锁
        if(lockShop(id)){
            //6.获取到互斥锁了,此时需要再对缓存中是否有数据进行判断
            //6.1 如果缓存中有数据直接返回
            if(judgeIfCacheExist(id)){
                return JSON.parseObject(stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_KEY + id),Shop.class);
            }
            try {
                //6.2缓存中没有数据再执行缓存重建的操作,开启独立线程,在独立线程中执行缓存重建的操作
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    setShopHotKey(id,30L);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //6.3释放锁
                unlockShop(id);
            }
        }

        //7.返回旧数据
        return shop;

    }
    /*
    为redis存入热点key
     */
    public void setShopHotKey(Long id , Long expireSeconds){
        //1.通过id查询到商铺的信息
        Shop shop = getById(id);

        //2.为其添加逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        //3.写入redis当中
        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_KEY + id,JSON.toJSONString(redisData));
    }

    /*
    判断缓存是否存在
     */
    public boolean judgeIfCacheExist(Long id){
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_KEY + id);
        if(StrUtil.isBlank(shopJson)){
            //不存在,返回false
            return false;
        }

        //存在,返回true
        return true;
    }
}
