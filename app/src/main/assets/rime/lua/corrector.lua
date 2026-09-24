-- 错音错字提示滤镜 (适配 Xime / Android 移动端)
-- 当用户输入易读错、易写错词汇时，在注释栏提示正确拼音或写法（如给予、按捺、莘莘学子）

local M = {}

function M.init(env)
    M.corrections = {
        ["给予"] = "jǐ yǔ",
        ["角逐"] = "jué zhú",
        ["主角"] = "zhǔ jué",
        ["角色"] = "jué sè",
        ["说服"] = "shuō fú",
        ["模样"] = "mú yàng",
        ["一模一样"] = "yī mú yī yàng",
        ["装模作样"] = "zhuāng mú zuò yàng",
        ["模板"] = "mú bǎn",
        ["按捺"] = "àn nà",
        ["按耐"] = "按捺(nà)",
        ["按耐不住"] = "按捺(nà)不住",
        ["曾经"] = "céng jīng",
        ["曾今"] = "曾经",
        ["莘莘学子"] = "shēn shēn xué zǐ",
        ["道行"] = "dào heng",
        ["自怨自艾"] = "zì yuàn zì yì",
        ["心宽体胖"] = "xīn kuān tǐ pán",
        ["埋怨"] = "mán yuàn",
        ["虚与委蛇"] = "xū yǔ wēi yí",
        ["木讷"] = "mù nè",
        ["龟裂"] = "jūn liè",
        ["荨麻疹"] = "xún má zhěn",
        ["大腹便便"] = "dà fù pián pián",
        ["卡脖子"] = "qiǎ bó zi",
        ["称职"] = "chèn zhí",
        ["螺蛳粉"] = "luó sī fěn",
        ["发酵"] = "fā jiào",
        ["酵母"] = "jiào mǔ",
        ["暖和"] = "nuǎn huo",
        ["关卡"] = "guān qiǎ",
        ["阈值"] = "yù zhí",
        ["蛤蜊"] = "gé lí",
        ["粗糙"] = "cū cāo",
        ["谄媚"] = "chǎn mèi",
        ["附骨之疽"] = "fù gǔ zhī jū",
        ["一曝十寒"] = "yī pù shí hán",
        ["粗犷"] = "cū guǎng",
        ["刚愎自用"] = "gāng bì zì yòng",
        ["买账"] = "mǎi zhàng",
    }
end

function M.func(input, env)
    local ctx = env.engine.context
    local disable_corrector = ctx and ctx:get_option("disable_corrector")
    local disable_custom_phrase = ctx and ctx:get_option("disable_custom_phrase")

    for cand in input:iter() do
        if disable_custom_phrase and (cand.type == "user_table" or cand.type == "table") then
            -- 跳过被禁用的自定义短语候选
        else
            if not disable_corrector then
                local tip = M.corrections[cand.text]
                if tip then
                    cand:get_genuine().comment = "〔" .. tip .. "〕"
                end
            end
            yield(cand)
        end
    end
end

return M
