package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopServiceImpl;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(100);

    @Test
    void testInsertHotKey(){
        shopServiceImpl.setShopHotKey(1L,1L);
    }

    @Test
    void testRedisIdWork(){
        Runnable runnable = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.createId("test");
                System.out.println(id);
            }
        };

        executorService.execute(runnable);
    }

    @Test
    void saveShopGeo(){
        //1.在数据库中查询得到所有的商铺
        List<Shop> shopList = shopServiceImpl.list();

        //2.根据shop的type_id去做分组
        Map<Long,List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        //3.遍历map,逐步添加缓存
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //获取typeId和同一类型的所有shop
            Long typeId = entry.getKey();
            List<Shop> value = entry.getValue();

            //遍历同一个类型的每一个shop,添加到缓存
            for (Shop shop : value) {
                Map<String, Point> geoMap = new HashMap<>();
                geoMap.put(shop.getId().toString(),new Point(shop.getX(),shop.getY()));
                stringRedisTemplate.opsForGeo().add("shop:geo:" + typeId , geoMap);
            }
        }


    }
}
