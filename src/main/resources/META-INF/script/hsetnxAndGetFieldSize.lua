local added = redis.call("HSETNX", KEYS[1], KEYS[2], ARGV[1])
local count = redis.call("HLEN", KEYS[1])
return cjson.encode({added, count})