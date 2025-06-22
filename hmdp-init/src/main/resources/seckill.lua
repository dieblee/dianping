local voucherId = ARGV[1]

local userId = ARGV[2]

local orderId = ARGV[3]

local stockKey='seckill:stock:' .. voucherId;
local orderKey='seckill:order:' .. voucherId


local stock = tonumber(redis.call('get', stockKey))

if stock == nil then
    --print("库存获取失败: " .. stockKey)
    return -1
end

if (stock<= 0) then
    --3.2.库存不足，返回1
    return 1
end


if(redis.call('sismember',orderKey,userId)==1) then
    --3.4.存在，说明重复下单，返回2
    return 2
end

redis.call('incrby',stockKey,-1)

redis.call('sadd',orderKey,userId)

redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0