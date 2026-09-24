-- 候选项置顶滤镜 (适配 Xime / Android 移动端)
-- 符合指定编码时，将配置的候选词优先排在前面

local M = {}

local function find_index(list, val)
    for i, v in ipairs(list) do
        if v == val then
            return i
        end
    end
    return 0
end

function M.init(env)
    local config = env.engine.schema.config
    env.name_space = env.name_space:gsub("^*", "")

    env.pin_cands = {}
    local list = config:get_list(env.name_space)
    if not list or list.size == 0 then
        return
    end

    for i = 0, list.size - 1 do
        local item = list:get_value_at(i).value
        if item and #item > 0 then
            -- 支持制表符分割或空格分割: "code\t词1 词2" 或 "code 词1 词2"
            local code, words
            if item:find("\t") then
                code, words = item:match("([^%t]+)%t+(.+)")
            else
                code, words = item:match("(%S+)%s+(.+)")
            end

            if code and words then
                code = code:lower():gsub(" ", "")
                local word_list = {}
                for w in words:gmatch("%S+") do
                    table.insert(word_list, w)
                end
                if #word_list > 0 then
                    env.pin_cands[code] = word_list
                end
            end
        end
    end
end

function M.func(input, env)
    local ctx = env.engine.context
    if (ctx and ctx:get_option("disable_pin_cand_filter")) or not env.pin_cands or next(env.pin_cands) == nil then
        for cand in input:iter() do
            yield(cand)
        end
        return
    end

    local raw_input = ctx and ctx.input and ctx.input:lower() or ""
    local pin_list = env.pin_cands[raw_input]

    -- 如果当前输入在置顶列表中未匹配，直接原样输出
    if not pin_list or #pin_list == 0 then
        for cand in input:iter() do
            yield(cand)
        end
        return
    end

    local pined = {}
    for _ = 1, #pin_list do
        table.insert(pined, false)
    end
    local others = {}
    local pined_count = 0
    local scanned = 0
    local max_scan = 80 -- 避免深层遍历影响输入法首屏流畅度

    for cand in input:iter() do
        scanned = scanned + 1
        local idx = find_index(pin_list, cand.text)
        if idx > 0 and not pined[idx] then
            pined[idx] = cand
            pined_count = pined_count + 1
        else
            table.insert(others, cand)
        end

        if pined_count == #pin_list or scanned >= max_scan then
            break
        end
    end

    -- 1. 先输出置顶的候选项
    for _, cand in ipairs(pined) do
        if cand then
            yield(cand)
        end
    end

    -- 2. 再输出其余已缓冲的候选项
    for _, cand in ipairs(others) do
        yield(cand)
    end

    -- 3. 输出剩余的流式候选项
    for cand in input:iter() do
        yield(cand)
    end
end

return M
