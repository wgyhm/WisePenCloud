pipeline {
    agent any

    // 环境变量配置中心
    environment {
        // 项目基础名称
        PROJECT_NAME = 'wisepencloud'
        // Docker 镜像仓库地址 (如果在单机内网部署，可以直接留空或使用 local)
        DOCKER_REGISTRY = 'local'
        // 动态获取 Git 提交的简短哈希作为镜像版本 Tag
        IMAGE_TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        // 部署脚本的绝对或相对路径
        COMPOSE_FILE_PATH = 'deploy/docker-compose-app.yml'
    }

    // 可选参数化构建，方便手动触发时选择分支
    parameters {
        string(name: 'BRANCH_NAME', defaultValue: 'main', description: '选择需要构建的 Git 分支')
    }

    stages {
        stage('1. 拉取代码 (Checkout)') {
            steps {
                echo "开始拉取 ${params.BRANCH_NAME} 分支代码..."
                checkout scm
                echo "✅ 代码拉取成功，当前构建版本 TAG: ${IMAGE_TAG}"
            }
        }

        stage('2. 编译与打包 (Build)') {
            parallel {
                stage('Java Backend Build') {
                    steps {
                        echo "开始进行 Java 微服务 Maven 构建..."
                        // 赋予 maven wrapper 执行权限
                        sh 'chmod +x ./mvnw'
                        // 多模块统一编译打包，跳过测试，开启单核多线程编译加快速度
                        sh './mvnw clean package -Dmaven.test.skip=true -T 1C'
                        sh '''
                        echo "开始整理构建产物..."
                        # 遍历所有服务模块
                        for dir in wisepen-*-service/*-biz; do
                            if [ -d "$dir/target" ]; then
                                # 精确查找 .jar 结尾，并排除 .original 和 -sources.jar 的包，重命名为 app.jar 放至模块根目录
                                find "$dir/target" -maxdepth 1 -name "*.jar" ! -name "*.original" ! -name "*-sources.jar" -exec cp {} "$dir/app.jar" \\;
                                echo "已成功提取: $dir/app.jar"
                            fi
                        done
                        '''
                    }
                }
            }
        }

        stage('2.5 准备可观测性探针 (OTel Agent)') {
            steps {
                echo "检查并下载 OpenTelemetry Java Agent..."
                // 如果本地没有，就自动下载到项目根目录供所有 Dockerfile COPY 使用
                sh '''
                if [ ! -f "opentelemetry-javaagent.jar" ]; then
                    echo "本地无探针，开始下载..."
                    curl -L -# -o opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
                else
                    echo "探针已存在，跳过下载"
                fi
                '''
            }
        }

        stage('3. 并行构建并推送镜像 (Docker Build & Push)') {
            // failFast true：任何一个微服务构建失败，立即停止整个流水线，避免资源浪费
            failFast true

            parallel {
                stage('User Service') {
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-user:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-user-service/wisepen-user-biz ."
                        }
                    }
                }
                stage('System Service') {
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-system:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-system-service/wisepen-system-biz ."
                        }
                    }
                }
                stage('Resource Service') {
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-resource:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-resource-service/wisepen-resource-biz ."
                        }
                    }
                }
                stage('Document Service') {
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-document:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-document-service/wisepen-document-biz ."
                        }
                    }
                }
                stage('Note Service') {
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-note:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-note-service/wisepen-note-biz ."
                        }
                    }
                }
                stage('File Storage Service') {
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-file-storage:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-file-storage-service/wisepen-file-storage-biz ."
                        }
                    }
                }
                stage('Fudan Extension Service') {
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-fudan-extension:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-fudan-extension-service/wisepen-fudan-extension-biz ."
                        }
                    }
                }
//                 stage('Note Collab Service') {
//                     steps {
//                         script {
//                             // Node.js 服务通常有独立的 Dockerfile 在其目录下
//                             dir('wisepen-note-collab-service') {
//                                 // 如果该目录下存在 Dockerfile，则使用以下命令构建
//                                 sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-note-collab:${IMAGE_TAG} ."
//                             }
//                         }
//                     }
//                 }
            }
        }

        stage('4. 自动化部署 (Deploy)') {
            environment {
                NACOS_USER = credentials('nacos-username')
                NACOS_PWD  = credentials('nacos-password')
            }
            steps {
                script {
                    echo "开始部署最新版本: ${IMAGE_TAG} ..."
                    // Jenkins 和 运行服务器 在同一台宿主机
                    sh """
                    # 导出版本号给 docker-compose 读取
                    export APP_VERSION=${IMAGE_TAG}
                    export DOCKER_REGISTRY=${DOCKER_REGISTRY}

                    # 重新拉取镜像并重启变更的服务
                    cd deploy
                    docker-compose -f docker-compose-app.yml up -d --remove-orphans
                    """

                    // 通过 Python 脚本远程部署到其他服务器
                    // sh "python3 deploy/dev-tools/remote_deploy.py --tag ${IMAGE_TAG} --registry ${DOCKER_REGISTRY}"
                }
            }
        }
    }

           // 后置处理钩子
    post {
        always {
            // 清理悬挂的无用镜像 (<none>:<none>)，防止长年累月撑爆 Jenkins 宿主机磁盘
            echo "执行 Docker 垃圾回收..."
            sh 'docker image prune -f'
        }
        success {
            echo "🎉 构建与部署大功告成！版本: ${IMAGE_TAG}"
            // 可以在这里增加 curl 命令发送钉钉/飞书 webhook 机器人通知
            // sh """
            // curl -X POST 'https://oapi.dingtalk.com/robot/send?access_token=xxx' \
            // -H 'Content-Type: application/json' \
            // -d '{"msgtype": "text", "text": {"content": "【WisePenCloud CI/CD】\n版本 ${IMAGE_TAG} 已成功部署！"}}'
            // """
        }
        failure {
            echo "❌ 流水线执行失败，请检查 Jenkins 控制台报错日志！"
        }
    }
}