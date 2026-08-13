local groupKeyIndex = #KEYS

if redis.call('GET', KEYS[groupKeyIndex]) ~= ARGV[2] then
    return 0
end

for index = 1, groupKeyIndex - 1 do
    if redis.call('GET', KEYS[index]) ~= ARGV[1] then
        return 0
    end
end

for index = 1, groupKeyIndex do
    redis.call('PEXPIRE', KEYS[index], ARGV[3])
end

return 1
