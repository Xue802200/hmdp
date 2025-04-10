package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopServiceImpl;
    @Resource
    private RedisIdWorker redisIdWorker;
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
}
