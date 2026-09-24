-- 长词优先滤镜 (适配 Xime / Android 移动端)
-- 提升西安、提案、图案、饥饿等优质长词的优先级

local M = {}

function M.init(env)
    local config = env.engine.schema.config
    env.name_space = env.name_space:gsub("^*", "")
    M.count = config:get_int(env.name_space .. "/count") or 2
    M.idx = config:get_int(env.name_space .. "/idx") or 3
end

function M.func(input, env)
    local ctx = env.engine.context
    if ctx and ctx:get_option("disable_long_word_filter") then
        for cand in input:iter() do
            yield(cand)
        end
        return
    end

    local l = {}
    local firstWordLength = 0
    local done = 0
    local i = 1

    for cand in input:iter() do
        local leng = utf8.len(cand.text) or 0
        if firstWordLength < 1 then
            firstWordLength = leng
        end

        if i < M.idx then
            i = i + 1
            yield(cand)
        elseif leng <= firstWordLength or cand.text:find("[%a%d]") then
            table.insert(l, cand)
        else
            yield(cand)
            done = done + 1
        end

        if done == M.count or #l > 40 then
            break
        end
    end

    for _, cand in ipairs(l) do
        yield(cand)
    end

    for cand in input:iter() do
        yield(cand)
    end
end

return M
