-- 中文大写数字与金额转换器 (适配 Xime / Android 移动端)
-- 触发方式：输入 v123 或 R123.45 等数字串，输出金额大写、数字大写、数字小写

local function yield_cand(seg, text, comment)
    local cand = Candidate("number", seg.start, seg._end, text, comment)
    cand.quality = 100
    yield(cand)
end

local M = {}

local digit_upper = { [0] = "零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖" }
local digit_lower = { [0] = "〇", "一", "二", "三", "四", "五", "六", "七", "八", "九" }

local unit_upper = { "", "拾", "佰", "仟" }
local unit_lower = { "", "十", "百", "千" }

local section_unit_upper = { "", "万", "亿", "万亿" }
local section_unit_lower = { "", "万", "亿", "万亿" }

local money_dec_upper = { "角", "分", "厘", "毫" }

-- 将 4 位以内的节转换为中文
local function format_section(section_str, is_upper)
    local digits = is_upper and digit_upper or digit_lower
    local units = is_upper and unit_upper or unit_lower
    local len = #section_str
    local res = ""
    local has_zero = false

    for i = 1, len do
        local n = tonumber(section_str:sub(i, i))
        local pos = len - i + 1
        if n == 0 then
            has_zero = true
        else
            if has_zero and res ~= "" then
                res = res .. digits[0]
            end
            res = res .. digits[n] .. units[pos]
            has_zero = false
        end
    end
    return res
end

-- 整数转换为中文
local function convert_int(int_str, is_upper)
    int_str = int_str:gsub("^0+", "")
    if int_str == "" then
        return is_upper and digit_upper[0] or digit_lower[0]
    end

    if #int_str > 16 then
        return "数值超限"
    end

    local digits = is_upper and digit_upper or digit_lower
    local sec_units = is_upper and section_unit_upper or section_unit_lower

    local sections = {}
    local len = #int_str
    while len > 0 do
        local start_pos = math.max(1, len - 3)
        table.insert(sections, int_str:sub(start_pos, len))
        len = start_pos - 1
    end

    local res = ""
    local need_zero = false

    for i = #sections, 1, -1 do
        local sec = sections[i]
        local sec_val = tonumber(sec)
        if sec_val > 0 then
            if need_zero and res ~= "" then
                res = res .. digits[0]
            end
            local sec_str = format_section(sec, is_upper)
            res = res .. sec_str .. sec_units[i]
            need_zero = sec_val < 1000 and i > 1
        else
            need_zero = true
        end
    end

    -- 小写读数时，如“一十”一般简读为“十”
    if not is_upper and res:find("^一十") then
        res = res:sub(4)
    end

    return res
end

-- 小数转换为逐位读法
local function convert_dec(dec_str, is_upper)
    local digits = is_upper and digit_upper or digit_lower
    local res = ""
    for i = 1, #dec_str do
        local n = tonumber(dec_str:sub(i, i))
        if n then
            res = res .. digits[n]
        end
    end
    return res
end

-- 人民币大写金额转换
local function convert_money(int_str, dec_str)
    local int_res = convert_int(int_str, true)
    if int_res == "数值超限" then
        return int_res
    end

    local result = ""
    if int_res ~= digit_upper[0] then
        result = int_res .. "元"
    end

    local dec_part = ""
    if dec_str and dec_str ~= "" then
        local len = math.min(#dec_str, 4)
        local has_zero = false
        for i = 1, len do
            local n = tonumber(dec_str:sub(i, i))
            if n and n > 0 then
                if has_zero and dec_part ~= "" then
                    dec_part = dec_part .. digit_upper[0]
                end
                dec_part = dec_part .. digit_upper[n] .. money_dec_upper[i]
                has_zero = false
            else
                has_zero = true
            end
        end
    end

    if dec_part == "" then
        if result == "" then
            return "零元整"
        end
        return result .. "整"
    else
        if result == "" then
            return dec_part
        end
        return result .. dec_part
    end
end

function M.init(env)
    local config = env.engine.schema.config
    env.name_space = env.name_space:gsub("^*", "")
    M.prefix = config:get_string(env.name_space .. "/prefix") or "v"
end

function M.func(input, seg, env)
    local ctx = env.engine.context
    if ctx and ctx:get_option("disable_number_translator") then
        return
    end

    local prefix = input:sub(1, 1)
    if prefix ~= "v" and prefix ~= "R" and prefix ~= "V" then
        return
    end

    local num_str = input:sub(2)
    if not num_str:match("^%d+%.?%d*$") then
        return
    end

    local int_part, dot, dec_part = num_str:match("^(%d*)(%.?)(%d*)$")
    if not int_part or int_part == "" then
        int_part = "0"
    end

    -- 1. 金额大写
    local money_text = convert_money(int_part, dec_part)
    yield_cand(seg, money_text, "〔金额大写〕")

    -- 2. 数字大写
    local upper_text = convert_int(int_part, true)
    if dot == "." and dec_part ~= "" then
        upper_text = upper_text .. "点" .. convert_dec(dec_part, true)
    end
    yield_cand(seg, upper_text, "〔数字大写〕")

    -- 3. 数字小写
    local lower_text = convert_int(int_part, false)
    if dot == "." and dec_part ~= "" then
        lower_text = lower_text .. "点" .. convert_dec(dec_part, false)
    end
    yield_cand(seg, lower_text, "〔数字小写〕")
end

return M
