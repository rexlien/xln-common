local count = redis.call("GET", KEYS[1])
if(count == nil or count == 0) then
    return cjson.encode(0)
else
    if(count == 1) then
        redis.call("DEL", KEYS[1])
        return cjson.encode(0)
    end
    return cjson.encode(redis.call("DECR", KEYS[1]))
end

