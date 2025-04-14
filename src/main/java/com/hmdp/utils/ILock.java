package com.hmdp.utils;

/**
 * 基于redis实现的分布式锁
 */
public interface ILock {

    /**
     * 获取分布式锁
     * @param ttl  锁的过期时间
     */
     boolean tryLock(Long ttl);

    /**
     * 释放锁
     */
    void unLock();
}
