local added = redis.call("SADD", KEYS[1], ARGV[1])
local count = redis.call("SCARD", KEYS[1])
local ret = {added, count}
return cjson.encode(ret)