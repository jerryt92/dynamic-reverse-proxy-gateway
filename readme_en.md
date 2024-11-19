### README.md

# Dynamic Reverse Proxy Gateway

This is a dynamic reverse proxy gateway project implemented based on Netty. It can dynamically forward requests to different backend servers based on the Host header information of the request.

## Features

- Dynamic routing: Parse the target host and port based on the Host header information of the request.
- Supports HTTP and HTTPS protocols.
- Supports WebSocket protocol.
- High-performance network communication using Netty.
- Logging using Log4j.

## Known Issues

- ~~The gateway's HTTPS support has issues and cannot handle HTTPS requests correctly.~~
- ~~Does not support WebSocket protocol.~~ Supports WebSocket by caching HTTP routing information.

## Dependencies

- Java 11
- Maven
- Netty 4.1.114.Final
- Log4j 2.17.1
- Commons Lang 3.14.0

## Quick Start

1. Clone the project to your local machine:
    ```sh
    git clone https://github.com/jerryt92/dynamic-reverse-proxy-gateway.git
    ```

2. Enter the project directory and build the project using Maven:
    ```sh
    cd dynamic-reverse-proxy-gateway
    mvn clean install
    ```

3. Run the gateway:

   `GatewayStarter`

4. Access [http://jerryt92.github.io.443.proxy.localhost:8888](http://jerryt92.github.io.443.proxy.localhost:8888) using a browser, and the gateway will forward the request to `https://jerryt92.github.io`.

## License

This project is open-sourced under the MIT License. See the `LICENSE` file for details.