local added = redis.call("HSETNX", KEYS[1], ARGV[1], ARGV[2])
local count = redis.call("HLEN", KEYS[1])
return cjson.encode({added, count})