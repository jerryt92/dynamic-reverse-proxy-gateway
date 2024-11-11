### README.md

#### English

For the English version of this README, please refer to [README_en.md](readme_en.md).

#### 中文

# 动态反向代理网关

这是一个基于 Netty 实现的动态反向代理网关项目。它能够根据请求的泛域名信息动态地将请求转发到不同的后端服务器。

## 特性

- 动态路由：根据请求的泛域名信息解析目标主机和端口。
- 支持路由到 HTTP 和 HTTPS 协议。
- 支持WebSocket协议。
- 使用 Netty 进行高性能网络通信。
- 使用 Log4j 进行日志记录。

## 存在的问题

- 网关的HTTPS支持存在问题，无法正确处理HTTPS请求。
- ~~无法支持WebSocket协议。~~ 通过缓存HTTP路由信息的方式支持了WebSocket。

## 依赖

- Java 11
- Maven
- Netty 4.1.114.Final
- Log4j 2.17.1
- Commons Lang 3.14.0

## 快速开始

1. 克隆项目到本地：
    ```sh
    git clone https://github.com/jerryt92/dynamic-reverse-proxy-gateway.git
    ```

2. 进入项目目录并使用 Maven 构建项目：
    ```sh
    cd dynamic-reverse-proxy-gateway
    mvn clean install
    ```

3. 运行网关：

   `GatewayStarter`

4. 使用浏览器访问 [http://jerryt92.github.io.443.proxy.localhost:8888](http://jerryt92.github.io.443.proxy.localhost:8888)，网关会将请求转发到 `https://jerryt92.github.io`。

## 许可证

本项目基于 MIT 许可证开源。详见 `LICENSE` 文件。