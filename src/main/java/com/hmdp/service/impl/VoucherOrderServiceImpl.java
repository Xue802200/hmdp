package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    private static final String keyPrefix = "secKill";

    /*
    用户进行下单秒杀
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.根据id查找出这个券的信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始
        //2.1 秒杀没开始
        if(LocalDateTime.now().isBefore(seckillVoucher.getCreateTime())){
            return Result.fail(SystemConstants.Seckill_NOT_BEGIN);
        }

        //2.2秒杀已经结束
        if(LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
            return Result.fail(SystemConstants.Seckill_END);
        }

        //3.查找是否还有库存
        Integer stock = seckillVoucher.getStock();
        if(stock <= 0){
            return Result.fail(SystemConstants.Seckill_OUT_COUNT);
        }

        //4.秒杀券还有库存,可以进行购买
        //用户的id作为锁对象
        Long userId = UserHolder.getUser().getId();

        //创建分布式锁对象
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(keyPrefix + userId, stringRedisTemplate);


        //获取锁
        boolean result = simpleRedisLock.tryLock(20L);

        //没拿到锁,直接报错即可
        if(!result){
            return Result.fail("一个用户只能下一单!");
        }

        try {
            //拿到当前代理的对象,避免this直接调用
            IVoucherOrderService currentProxy = (IVoucherOrderService) AopContext.currentProxy();
            return currentProxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            simpleRedisLock.unLock();
        }

    }

    /*
    为用户秒杀生成订单
     */
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        //完成用户一人只能下一旦的操作  采用乐观锁的方式实现

        //下单之前,去订单表中查看是否有记录
        Integer count = query().eq("user_id", UserHolder.getUser().getId())
                .eq("voucher_id", voucherId).count();

        //如果有记录,那么就不能完成购买操作
        if( count != 0){
            return Result.fail(SystemConstants.Seckill_CANNOT_BUY);
        }

        //进行购买
        //sql语句让库存数stock -1
        //为什么判断条件是stock > 0 ?  --> 1.为了解决让version版本号前后为1导致的失败率过低的问题
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .ge("stock", 0)
                .update();

        if( !result){
            return Result.fail("购买失败！");
        }

        //生成唯一的订单号
        long id = redisIdWorker.createId(keyPrefix);

        //将订单信息插入到order表中
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(id);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());

        saveOrUpdate(voucherOrder);

        return Result.ok(id);
    }
}
