local groupKeyIndex = #KEYS

if redis.call('GET', KEYS[groupKeyIndex]) ~= ARGV[2] then
    return 0
end

for index = 1, groupKeyIndex - 1 do
    if redis.call('GET', KEYS[index]) ~= ARGV[1] then
        return 0
    end
end

redis.call('DEL', unpack(KEYS))
return 1
