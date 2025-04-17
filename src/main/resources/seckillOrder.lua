--判断秒杀库存,一人一单,决定用户是否抢购成功
local voucherId = ARGV[1]
local userId = ARGV[2]
local id = ARGV[3]

local seckillKey = 'cache:seckillStock:' .. voucherId
local orderKey = 'cache:order:' .. userId
--判断库存信息
if(tonumber(redis.call('get',seckillKey)) <= 0) then
    return 1;
end
--判断一人一单操作
if(redis.call('SISMEMBER',orderKey, userId) == 1) then
    return 2;
end

--更新库存信息
redis.call('INCRBY',seckillKey,-1)
--实现购买操作
redis.call('SADD',orderKey,userId)

--判断用户可以购买,则将voucherId,userId,订单id写入消息队列中
redis.call('XADD','stream.orders','*','userId',userId,'voucherId',voucherId,'id',id)
return 0