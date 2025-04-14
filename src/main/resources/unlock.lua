-- 判断缓存中的id和当前的threadId是否相同
if (redis.call('GET',KEYS[1]) == ARGVS[1]) then
    --相同则删除key
    redis.call('DELETE',KEYS[1])
end
return 0