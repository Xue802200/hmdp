package com.hmdp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id) throws JsonProcessingException;

    /**
     * 更新商铺信息
     * @param shop  更新后的商铺信息
     * @return
     */
    Result update(Shop shop);


    /*
    根据用户当前距离查找商铺信息
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
