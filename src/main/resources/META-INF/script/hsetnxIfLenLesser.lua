-- return 0: succeeded, 1: hast field already set 2: over limit
local count = redis.call("HLEN", KEYS[1])
if(count >=  tonumber(ARGV[3])) then
    return cjson.encode(2)
end
local result = redis.call("HSETNX", KEYS[1], ARGV[1], ARGV[2])
if(result == 0) then
    return cjson.encode(1)
else
    return cjson.encode(0)
end