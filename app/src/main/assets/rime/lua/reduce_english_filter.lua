-- 降低部分英语单词在候选项的位置 (适配 Xime / Android 移动端)
-- 解决英文混输开启时，短英语单词挤占正常拼音候选的问题

local M = {}

function M.init(env)
    local config = env.engine.schema.config
    env.name_space = env.name_space:gsub("^*", "")
    M.idx = config:get_int(env.name_space .. "/idx") or 2

    local default_words = {
        "aid", "aim", "air", "and", "ann", "ant", "any", "bad", "bag", "bail", "bait", "bam",
        "ban", "band", "bang", "bank", "bans", "bar", "bat", "bay", "bend", "bent", "benz",
        "bib", "bid", "bien", "big", "bin", "bog", "bind", "bit", "biz", "bob", "boc", "bop",
        "bos", "bot", "bow", "box", "boy", "bud", "buf", "bug", "bus", "but", "buy", "cab",
        "cad", "cain", "cam", "can", "cans", "cap", "car", "cat", "cef", "cen", "cent", "chad",
        "chan", "chap", "chef", "chen", "cher", "chew", "chic", "chin", "chip", "chit", "coup",
        "cum", "cunt", "cup", "cur", "cut", "dab", "dad", "dag", "dal", "dam", "day", "def",
        "del", "den", "dent", "dew", "dial", "did", "died", "dies", "diet", "dig", "dim", "din",
        "dip", "dis", "dit", "doug", "dub", "dug", "dun", "dunn", "don", "fab", "fax", "fob",
        "fog", "for", "foul", "fox", "fun", "fur", "gag", "gain", "gal", "gam", "gan", "gang",
        "gap", "gas", "gay", "ged", "gel", "gem", "gen", "ger", "get", "guam", "gum", "gun",
        "guns", "gus", "gut", "guy", "had", "hail", "hair", "ham", "han", "hand", "hang", "hank",
        "hans", "has", "hat", "hay", "heil", "heir", "hem", "hen", "hep", "hex", "hey", "hud",
        "hum", "hung", "hunk", "hunt", "hut", "jim", "jug", "kat", "kent", "key", "lab", "lad",
        "lag", "laid", "lam", "lan", "land", "lang", "laos", "lap", "lat", "law", "lax", "lay",
        "led", "leg", "let", "lex", "liam", "lib", "lid", "lied", "lien", "lies", "ling", "link",
        "linn", "lip", "lit", "liz", "lob", "log", "lot", "loud", "low", "lug", "lund", "lung",
        "lux", "mag", "maid", "mail", "main", "man", "mann", "many", "map", "mar", "mat", "max",
        "may", "med", "mel", "men", "mend", "mens", "ment", "met", "mil", "min", "mind", "ming",
        "mins", "mint", "mob", "moc", "mod", "mom", "mop", "mos", "mot", "mud", "mug", "mum",
        "nail", "nan", "nap", "nat", "nay", "neil", "net", "new", "nib", "nil", "nip", "noun",
        "nous", "nun", "nut", "our", "out", "pac", "pad", "paid", "pail", "pain", "pair", "pak",
        "pal", "pam", "pan", "pans", "pant", "pap", "par", "pat", "paw", "pax", "pay", "pens",
        "pic", "pier", "pies", "pig", "pin", "ping", "pink", "pins", "pint", "pit", "pix", "pod",
        "pop", "pos", "pot", "pour", "pow", "pub", "put", "rand", "rang", "rank", "rant", "red",
        "rent", "rep", "res", "ret", "rex", "rib", "rid", "rig", "rim", "rip", "rub", "rug",
        "ruin", "rum", "run", "runc", "runs", "sac", "sad", "said", "sail", "sal", "sam", "san",
        "sand", "sang", "sans", "sap", "sat", "saw", "sax", "say", "sec", "send", "sent", "set",
        "sew", "sex", "sham", "shaw", "shed", "shin", "ship", "shit", "shut", "sig", "sim", "sin",
        "sip", "sir", "sis", "sit", "six", "soul", "soup", "sour", "sub", "suit", "sum", "sun",
        "sung", "suns", "sup", "sur", "sus", "tab", "tad", "tag", "tail", "taj", "tan", "tang",
        "tank", "tap", "tar", "tax", "tec", "ted", "tel", "ten", "ter", "tex", "tic", "tied",
        "tier", "ties", "tim", "tin", "tip", "tit", "tour", "tout", "tum", "wag", "wait", "wail",
        "wan", "wand", "womens", "want", "wap", "war", "was", "wax", "way", "weir", "went", "won",
        "wow", "yan", "yang", "yen", "yep", "yes", "yet", "yin", "your", "yum", "zen", "zip"
    }

    M.map = {}
    for _, w in ipairs(default_words) do
        M.map[w] = true
    end

    local list = config:get_list(env.name_space .. "/words")
    if list and list.size > 0 then
        for i = 0, list.size - 1 do
            local w = list:get_value_at(i).value
            if w and #w > 0 then
                M.map[w:lower()] = true
            end
        end
    end
end

function M.func(input, env)
    local ctx = env.engine.context
    if (ctx and ctx:get_option("disable_reduce_english_filter")) or not ctx or not ctx.input then
        for cand in input:iter() do
            yield(cand)
        end
        return
    end

    local code = ctx.input:lower()
    if not M.map[code] then
        for cand in input:iter() do
            yield(cand)
        end
        return
    end

    local pending = {}
    local count = 0
    for cand in input:iter() do
        count = count + 1
        -- 如果是纯英文单词，暂存降权；否则立即输出
        if cand.text:match("^[a-zA-Z]+$") then
            table.insert(pending, cand)
        else
            yield(cand)
        end
        if count >= M.idx + #pending then
            break
        end
    end

    for _, cand in ipairs(pending) do
        yield(cand)
    end

    for cand in input:iter() do
        yield(cand)
    end
end

return M
