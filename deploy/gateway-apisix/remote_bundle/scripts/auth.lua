-- =============================================================================
-- APISIX 鉴权后置脚本 (提取 JWT Payload 并注入 Header)
-- 此脚本依赖 APISIX 内置的 resty.jwt 库
-- =============================================================================

return function(conf, ctx)
    -- 引入必要的模块
    local core = require("apisix.core")
    local jwt = require("resty.jwt")

    -- 获取 Authorization 头
    -- Nginx 获取 Header 是大小写不敏感的，但通常标准是 Authorization
    local auth_header = core.request.header(ctx, "Authorization")

    -- 如果没有 Token，直接 return 结束当前脚本。
    -- 不要手动 return 401，因为后面的 jwt-auth 插件会发现缺失并返回标准的 401。
    if not auth_header then
        return
    end

    -- 处理 Bearer 前缀 (兼容性处理)
    --有些客户端会发 "Bearer <token>"，有些只发 "<token>"
    local token = auth_header
    if string.sub(auth_header, 1, 7) == "Bearer " then
        token = string.sub(auth_header, 8)
    end

    if not token or token == "" then
        return
    end

    -- load_jwt 不会验证签名，只解析内容
    -- 即使这里解析了假 Token，稍后执行的 jwt-auth 插件也会拦截请求，所以是安全的
    local jwt_obj = jwt:load_jwt(token)

    -- 防止解析失败 (如传入了非 JWT 格式乱码) 导致脚本崩溃
    if not jwt_obj or not jwt_obj.payload then
        -- 解析失败也不报错，直接交给 jwt-auth 去拦截无效 Token
        return
    end

    -- 提取 Payload 数据
    local payload = jwt_obj.payload

    -- 注入 Header 传给下游 Java 服务

    -- 辅助函数：安全转换为字符串，如果是 nil 则返回 nil
    local function safe_tostring(val)
        if val == nil or val == ngx.null then
            return nil
        end
        return tostring(val)
    end

    -- 注入 User ID
    local loginId = safe_tostring(payload.loginId)
    if payload.loginId then
        core.request.set_header(ctx, "X-User-Id", loginId)
    end
    -- 注入 Identity Type
    local identityType = safe_tostring(payload.identityType)
    if identityType then
        core.request.set_header(ctx, "X-Identity-Type", identityType)
    end
    -- 注入 Group IDs
    local groupIds = safe_tostring(payload.groupIds)
    if groupIds then
        core.request.set_header(ctx, "X-Group-Ids", groupIds)
    end
end