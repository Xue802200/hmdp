package com.hmdp.service.impl;

import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
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
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RedisIdWorker redisIdWorker;

    //order表存入缓存中的id
    public static final String keyPrefix = "seckillVoucher";

    @Override
    public synchronized Result seckillVoucher(Long voucherId) {
        //1.先根据id去查找对应的优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();

        //2.1秒杀尚未开始
        if(LocalDateTime.now().isBefore(beginTime)){
            return Result.fail(SystemConstants.Seckill_NOT_BEGIN);
        }

        //2.2秒杀已经结束
        if(LocalDateTime.now().isAfter(endTime)){
            return Result.fail(SystemConstants.Seckill_END);
        }

        //3.如果没有过期,则去查询库存中是否还有
        Integer stock = seckillVoucher.getStock();

        //4.如果库存没有了,返回错误
        if(stock <= 0){
            return Result.fail(SystemConstants.Seckill_OUT_COUNT);
        }

        //完成下单的业务逻辑
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            //1.拿到当前对象的代理对象,避免因为对象本身的调用导致事务失效,通过手动获取代理对象的方式去调用
            IVoucherOrderService orderService = (IVoucherOrderService) AopContext.currentProxy();

            //通过代理对象的调用避免事务的失效
            return orderService.createVoucherOrder(voucherId);
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5.要实现一个用户只能买入一单的操作
        Integer count = query().eq("user_id", UserHolder.getUser().getId())
                .eq("voucher_id", voucherId).count();
        //count不等于0,说明用户买过,报错
        if(count != 0){
            return Result.fail(SystemConstants.Seckill_CANNOT_BUY);
        }

        //6.如果库存没有过期,则完成买入操作
        //6.1 将优惠券的库存数量-1
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")  //set sql = sql -1
                .eq("voucher_id", voucherId)      //where id = voucherId
                .gt("stock", 0)
                .update();

        //6.2生成对应的订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        //主键id
        long orderId = redisIdWorker.createId(keyPrefix);
        voucherOrder.setId(orderId);
        //下单的用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //购买的代金券id
        voucherOrder.setVoucherId(voucherId);

        //7.写入订单
        voucherOrderService.save(voucherOrder);

        return Result.ok(orderId);
    }
}
