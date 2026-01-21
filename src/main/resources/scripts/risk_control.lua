-- risk_control.lua
-- KEYS[1]: 风险画像键 (e.g., "audit:risk:{userId}")
-- KEYS[2]: 观察窗口键 (e.g., "audit:window:{userId}")
-- ARGV[1]: 当前操作风险分 (incoming_score)
-- ARGV[2]: 当前时间戳 (now, seconds)
-- ARGV[3]: 衰减速率 (decay_rate, points/second)
-- ARGV[4]: 观察阈值 (obs_threshold)
-- ARGV[5]: 阻断阈值 (block_threshold)
-- ARGV[6]: 窗口有效期 (window_ttl, seconds)

local profile_key = KEYS[1]
local window_key = KEYS[2]
local incoming_score = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local decay_rate = tonumber(ARGV[3])
local obs_limit = tonumber(ARGV[4])
local block_limit = tonumber(ARGV[5])
local win_ttl = tonumber(ARGV[6])

-- 1. 获取当前状态
local score = 0
local last_time = now
local state = "NORMAL"

local profile = redis.call('HMGET', profile_key, 'score', 'last_time', 'state')
if profile and profile[1] then
    score = tonumber(profile[1]) or 0
    last_time = tonumber(profile[2]) or now
    state = profile[3] or "NORMAL"
end

-- 2. 计算时间衰减 (Time Decay)
-- 风险分随时间自然衰减，模拟用户行为的短期记忆
local time_passed = now - last_time
if time_passed > 0 then
    local decay = time_passed * decay_rate
    score = score - decay
    if score < 0 then score = 0 end
end

-- 3. 累加新风险 (Accumulate New Risk)
score = score + incoming_score

-- 4. 状态流转逻辑 (State Transition Logic)
local final_action = "ALLOW"
local final_state = state

if score >= block_limit then
    -- 达到阻断阈值 -> 立即阻断
    final_state = "BLOCKED"
    final_action = "BLOCK"
    -- 既然已经阻断，可以清除观察窗口（或者保留以供分析，这里选择清除以简化逻辑）
    redis.call('DEL', window_key)

elseif score >= obs_limit then
    -- 达到观察阈值
    if state == "NORMAL" then
        -- 首次进入观察状态 -> 警告并开启时间窗口
        final_state = "OBSERVATION"
        final_action = "WARNING"
        -- 设置窗口 Key，过期时间为 TTL
        redis.call('SETEX', window_key, win_ttl, "1")
    elseif state == "OBSERVATION" then
        -- 已经在观察状态
        if redis.call('EXISTS', window_key) == 0 then
            -- 窗口已过期，但分数仍高 -> 升级为阻断 (持续高风险)
            final_state = "BLOCKED"
            final_action = "BLOCK"
        else
            -- 窗口期内 -> 维持警告
            final_action = "WARNING"
        end
    else
        -- 已经是 BLOCKED 状态
        if state == "BLOCKED" then
            final_action = "BLOCK"
        end
    end
else
    -- 分数较低 -> 恢复正常
    final_state = "NORMAL"
    final_action = "ALLOW"
    -- 清除窗口 Key (表现良好，重置观察)
    redis.call('DEL', window_key)
end

-- 5. 保存状态
redis.call('HMSET', profile_key, 'score', score, 'last_time', now, 'state', final_state)
-- 设置画像过期时间 (例如 1 天)，防止死数据堆积
redis.call('EXPIRE', profile_key, 86400)

-- 返回: [状态, 分数, 动作]
return {final_state, tostring(score), final_action}
