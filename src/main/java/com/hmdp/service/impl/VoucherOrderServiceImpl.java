package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
   @Resource
   private ISeckillVoucherService seckillVoucherService;
   @Resource
   private RedisIdWorker redisIdWorker;
   @Resource
   private StringRedisTemplate stringRedisTemplate;

    //执行lua脚本
   private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    static{
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setLocation(new ClassPathResource("seckillOrder.lua"));
        REDIS_SCRIPT.setResultType(Long.class);
    }


   /*
   保证从阻塞队列中拿取信息的操作在这个对象被创建时就执行了
    */
   @PostConstruct
   public void init(){
       SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());

   }

    //开启一个单独的线程去执行业务下单的操作
    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private  class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true) {
                    //1.从消息队列中拿取数据 ->  XREADGROUP Group g1 c1 Count 1 Block 2000 STREAMS stream.orders >
                try {
                    String keyName = "stream.orders";
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(keyName, ReadOffset.lastConsumed())
                    );

                    //2.判断是否拿取到数据,没有拿到直接循环
                    if (list == null || list.isEmpty()) {
                        continue;
                    }

                    //3.拿到了数据,获取保存在mq中的voucherId,userId,id
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> valueMap = record.getValue();  //存储了voucherId-userId的数据

                    //封装voucherOrder
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(valueMap, new VoucherOrder(), true);

                    //4.保存到数据库
                    save(voucherOrder);

                    //5.ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(keyName,"g1",record.getId());
                } catch (Exception e) {
                    //处理pending-list中的信息
                    log.info("正在消除pending-list中的消息",e);
                    pendinglistHandler();
                }
            }
        }

        /**
         * 将消息队列中的pending-list中的数据移除
         */
        private void pendinglistHandler() {
            while (true) {
                //1.从pending-list中拿取数据 ->  XREADGROUP Group g1 c1 Count 1 STREAMS stream.orders 0
                try {
                    String keyName = "stream.orders";
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(keyName, ReadOffset.from("0"))
                    );

                    //2.如果pendinglist中没有数据，说明解决完了,跳出循环即可
                    if (list == null || list.isEmpty()) {
                        break;
                    }

                    //3.拿到了数据,获取保存在mq中的voucherId,userId,id
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> valueMap = record.getValue();  //存储了voucherId-userId的数据

                    //封装voucherOrder
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(valueMap, new VoucherOrder(), true);

                    //4.保存到数据库
                    save(voucherOrder);

                    //5.ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(keyName,"g1",record.getId());
                } catch (Exception e) {
                    //线程休眠，避免一直循环
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

//    //阻塞队列
//    private static final BlockingQueue<VoucherOrder> seckill_voucher_queue = new ArrayBlockingQueue<>(1024*1024);
//
//    private  class VoucherOrderHandler implements Runnable{
//       @Override
//       public void run() {
//           while (true) {
//               //从阻塞队列中拿取订单id,userId,threadId
//               try {
//                   VoucherOrder voucherOrder = seckill_voucher_queue.take();
//                   //用户秒杀下单
//                   save(voucherOrder);
//               } catch (Exception e) {
//                   log.info("订单创建失败:{}",e.getMessage());
//               }
//           }
//       }
//   }


    /*
 用户进行秒杀下单的接口
  */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //获取当前订单id
        long id = redisIdWorker.createId("seckillVoucher");
        //1.执行lua脚本
        long result = stringRedisTemplate.execute(
                REDIS_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(id)
        );
        //2.判断返回的结果
        if(result != 0){
            //2.1结果非0,返回错误信息
            return Result.fail(result == 1 ? "秒杀券售空!":"当前用户不可重复购买");
        }

        //4.返回订单id
        return Result.ok(id);

    }

//    /*
//    用户进行秒杀下单的接口
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询秒杀券的信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//
//        //2.判断当前是否在秒杀场景下
//        if(LocalDateTime.now().isBefore(seckillVoucher.getCreateTime())){  //秒杀尚未开始
//            return Result.fail("秒杀还没有开始");
//        }
//
//        if(LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
//            return Result.fail("秒杀已经结束");
//        }
//
//        //3.查找是否还有库存
//        if(seckillVoucher.getStock() <= 0){
//            return Result.fail("秒杀券已经被抢光了");
//        }
//
//        //4.可以进行秒杀,先获取一把分布式锁,用户id作为锁对象
//        Long threadId = UserHolder.getUser().getId();
//        RLock lock = redissonClient.getLock("lock:seckill:" + threadId);
//
//        //尝试获取锁
//        if(!lock.tryLock()){
//            //获取锁失败
//            return Result.fail("一个用户只能下一单!");
//        }
//
//        //进行秒杀的业务逻辑
//        try {
//            IVoucherOrderService currentProxy = (IVoucherOrderService) AopContext.currentProxy();
//            return  currentProxy.createVoucherOrder(voucherId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//    }

    /*
    用户秒杀下单的具体业务逻辑
     */
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        //1.先查询当前用户是否已经购买过
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        //2.购买过返回错误信息
        if(seckillVoucher != null){
            return Result.fail("购买过的商品不可再次购买！");
        }

        //3.进行秒杀,更新seckill表数据
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .ge("stock", 0).update();

        //4.判断是否购买成功
        if(!result){
            return Result.fail("已售空,购买失败！");
        }

        //成功,生成订单信息
        long id = redisIdWorker.createId("secKill:");

        //将订单信息插入到order表中
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(id);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());

        saveOrUpdate(voucherOrder);

        return Result.ok(id);
    }
}
