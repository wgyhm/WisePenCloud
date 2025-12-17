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

    -- 安全校验：如果没带 Token，直接 401
    if not auth_header then
        core.log.warn("Missing Authorization Header")
        return 401, {message = "Missing Token"}
    end

    -- 处理 Bearer 前缀 (兼容性处理)
    --有些客户端会发 "Bearer <token>"，有些只发 "<token>"
    local token = auth_header
    if string.sub(auth_header, 1, 7) == "Bearer " then
        token = string.sub(auth_header, 8)
    end

    -- 解析 JWT (注意：这里不做验签，因为 jwt-auth 插件已经帮我们验过了)
    -- 我们这里 load_jwt 主要是为了拿 payload 数据
    local jwt_obj = jwt:load_jwt(token)

    -- 提取 Payload 数据
    local payload = jwt_obj.payload

    -- 注入 Header 传给下游 Java 服务
    -- Nginx 的 Header 值必须是字符串！如果 loginId 是数字 (如 1001)，直接传会导致 500 错误，必须用 tostring() 转换
    if payload.loginId then
        core.request.set_header(ctx, "X-User-Id", tostring(payload.loginId))
    end

    if payload.identityType then
        core.request.set_header(ctx, "X-Identity-Type", tostring(payload.identityType))
    end

    if payload.groupIds then
        -- groupIds 可能是 nil，tostring(nil) 会变成 "nil" 字符串，通常我们希望它不传或者传空
        core.request.set_header(ctx, "X-Group-Ids", tostring(payload.groupIds))
    end
end