#!/bin/bash

# ================= 环境变量定义 =================
APISIX_ADMIN="http://127.0.0.1:9180"
ADMIN_KEY="edd1c9f034335f136f87ad84b625c8f1"

# 鉴权相关配置 (可从 Jenkins/GitLab CI 注入)
CONSUMER_NAME="wisepen_global_consumer"
CONSUMER_KEY="wisepen-app"
JWT_SECRET="wisepen-secret-888"

# 鉴权模版 ID (约定 1 为全局鉴权模版)
AUTH_TEMPLATE_ID=1

# ================= 核心函数定义 =================

# 初始化全局鉴权模版 (Consumer + PluginConfig)
# 每次部署都会强制刷新鉴权逻辑
function init_infrastructure() {
    echo ">>> [1/3] 正在加载 Lua 脚本..."
    # 读取 Lua 文件并进行转义处理，确保 JSON 格式正确
    if [ ! -f "./scripts/auth.lua" ]; then
        echo "Error: ./scripts/auth.lua 文件不存在！"
        exit 1
    fi
    LUA_SCRIPT=$(cat ./scripts/auth.lua | sed 's/"/\\"/g' | tr -d '\n')

    echo ">>> [2/3] 初始化消费者 (Consumer)..."
    # 使用 PUT 确保覆盖
    curl -s -o /dev/null "${APISIX_ADMIN}/apisix/admin/consumers" -X PUT \
      -H "X-API-KEY: ${ADMIN_KEY}" \
      -d "{
        \"username\": \"${CONSUMER_NAME}\",
        \"plugins\": {
          \"jwt-auth\": {
            \"key\": \"${CONSUMER_KEY}\",
            \"secret\": \"${JWT_SECRET}\"
          }
        }
      }"

    echo ">>> [3/3] 初始化通用鉴权模版 (Plugin Config ID: ${AUTH_TEMPLATE_ID})..."
    # 这里定义了所有微服务通用的鉴权逻辑
    # 注意：这里不绑定任何 Upstream，纯粹是逻辑
    curl -s -o /dev/null "${APISIX_ADMIN}/apisix/admin/plugin_configs/${AUTH_TEMPLATE_ID}" -X PUT \
      -H "X-API-KEY: ${ADMIN_KEY}" \
      -d "{
        \"desc\": \"WisePen Global Auth Template\",
        \"plugins\": {
            \"jwt-auth\": {},
            \"prometheus\": {},
            \"opentelemetry\": {},
            \"serverless-pre-function\": {
                \"phase\": \"rewrite\",
                \"functions\": [\"${LUA_SCRIPT}\"]
            }
        }
    }"
    echo -e "\n基础设施初始化完成 (Consumer & Template Updated)."
}

# 函数 2: 注册微服务路由
# 参数: $1=路由ID, $2=路由名称, $3=URI路径, $4=Nacos服务名
function register_service_route() {
    local ROUTE_ID=$1
    local ROUTE_NAME=$2
    local URI_PATH=$3
    local NACOS_SERVICE=$4

    echo ">>> 正在注册服务: [${ROUTE_NAME}] -> Nacos: [${NACOS_SERVICE}]"

    curl -s -o /dev/null "${APISIX_ADMIN}/apisix/admin/routes/${ROUTE_ID}" -X PUT \
      -H "X-API-KEY: ${ADMIN_KEY}" \
      -d "{
        \"name\": \"${ROUTE_NAME}\",
        \"uri\": \"${URI_PATH}\",
        \"plugin_config_id\": ${AUTH_TEMPLATE_ID},
        \"upstream\": {
            \"type\": \"roundrobin\",
            \"discovery_type\": \"nacos\",
            \"service_name\": \"${NACOS_SERVICE}\"
        }
    }"
    echo "   └─ 路由 ID ${ROUTE_ID} 更新完毕."
}

# ================= 🚀 执行逻辑 =================

echo "========================================="
echo "   WisePen 网关自动化部署脚本"
echo "========================================="

init_infrastructure

echo -e "\n-----------------------------------------"

# 注册业务服务 (未来加服务只用在这里加一行)
# 格式: register_service_route  <ID>  <描述>  <路径>  <Nacos服务名>

# User 服务 (受保护)
register_service_route 2 "user-service-protected" "/user/*" "wisepen-user-service"

echo -e "\n========================================="
echo "所有配置已推送到 APISIX !"