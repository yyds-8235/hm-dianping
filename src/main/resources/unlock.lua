---@diagnostic disable: undefined-global
---
--- Created by hanchenyang.
--- DateTime: 2025/12/3 09:26
---
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0
