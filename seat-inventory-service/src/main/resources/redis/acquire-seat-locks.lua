local groupKeyIndex = #KEYS

for index = 1, groupKeyIndex do
    if redis.call('EXISTS', KEYS[index]) == 1 then
        return 0
    end
end

for index = 1, groupKeyIndex - 1 do
    redis.call('SET', KEYS[index], ARGV[1], 'PX', ARGV[3])
end

redis.call('SET', KEYS[groupKeyIndex], ARGV[2], 'PX', ARGV[3])
return 1
