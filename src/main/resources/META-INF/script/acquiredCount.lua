local count = redis.call("GET", KEYS[1])
if(count==nil or count==false) then
    redis.call("SET", KEYS[1], "1", "PX", ARGV[1], "NX")
    count = 1
else
    --count = count+1
    --local countStr = tostring(count)
    --count = redis.call("SET", KEYS[1], countStr, "XX", "KEEPTTL")
    count = redis.call("INCR", KEYS[1])
    if(count == 1) then
        redis.call("PEXPIRE", KEYS[1], ARGV[1])
    end
end
return cjson.encode(count)