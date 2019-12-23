local count = redis.call("INCR", KEYS[1])
if(count == 1)  then
    redis.call("PEXPIRE", KEYS[1], ARGV[1])
end
return cjson.encode(count)