package io.github.jerryt92.proxy.protocol.tcp;

import io.github.jerryt92.proxy.protocol.ProtocolType;
import io.netty.buffer.ByteBuf;

public class ProtocolDetection {
    private static final int MINIMUM_LENGTH = 4;

    public static ProtocolType detectProtocol(ByteBuf in) {
        if (in.readableBytes() < MINIMUM_LENGTH) {
            return ProtocolType.UNKNOWN;
        }
        in.markReaderIndex();
        byte[] initialBytes = new byte[MINIMUM_LENGTH];
        in.readBytes(initialBytes);
        in.resetReaderIndex();

        // Check for SSL/TLS first to avoid overlap with MQTT
        if (initialBytes[0] == 0x16 && initialBytes[1] == 0x03 && (initialBytes[2] >= 0x00 && initialBytes[2] <= 0x03)) {
            return ProtocolType.SSL_TLS;
        }

        // Check for HTTP
        if ((initialBytes[0] == 'G' && initialBytes[1] == 'E' && initialBytes[2] == 'T') || // GET
                (initialBytes[0] == 'P' && initialBytes[1] == 'O' && initialBytes[2] == 'S' && initialBytes[3] == 'T') || // POST
                (initialBytes[0] == 'H' && initialBytes[1] == 'T' && initialBytes[2] == 'T' && initialBytes[3] == 'P') || // HTTP
                (initialBytes[0] == 'P' && initialBytes[1] == 'U' && initialBytes[2] == 'T') || // PUT
                (initialBytes[0] == 'D' && initialBytes[1] == 'E' && initialBytes[2] == 'L') || // DELETE
                (initialBytes[0] == 'H' && initialBytes[1] == 'E' && initialBytes[2] == 'A' && initialBytes[3] == 'D') || // HEAD
                (initialBytes[0] == 'O' && initialBytes[1] == 'P' && initialBytes[2] == 'T' && initialBytes[3] == 'I') || // OPTIONS
                (initialBytes[0] == 'T' && initialBytes[1] == 'R' && initialBytes[2] == 'A' && initialBytes[3] == 'C') || // TRACE
                (initialBytes[0] == 'C' && initialBytes[1] == 'O' && initialBytes[2] == 'N' && initialBytes[3] == 'N')) { // CONNECT
            return ProtocolType.HTTP;
        }

        // Check for MQTT
        if ((initialBytes[0] & 0xF0) == 0x10) {
            return ProtocolType.MQTT;
        }

        // Check for SSH
        if (initialBytes[0] == 'S' && initialBytes[1] == 'S' && initialBytes[2] == 'H' && initialBytes[3] == '-') {
            return ProtocolType.SSH;
        }

        // Check for WebSocket
        if ((initialBytes[0] & 0x80) == 0x80 && (initialBytes[1] & 0x80) == 0x80) {
            return ProtocolType.WEBSOCKET;
        }

        // Unknown protocol
        return ProtocolType.UNKNOWN;
    }
}