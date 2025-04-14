package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    /**
     * Redisson自定义配置
     * @return
     */
    @Bean
    public RedissonClient redissonClient() {
        //配置类
        Config config = new Config();

        //添加Redis的地址,这里添加了单点的地址,也可以使用config.useClusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://192.168.100.104:6379").setDatabase(1);

        //创建客户端
        return Redisson.create(config);
    }
}
