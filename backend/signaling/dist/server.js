"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const ws_1 = require("ws");
const PORT = process.env.PORT ? parseInt(process.env.PORT) : 8080;
const wss = new ws_1.WebSocketServer({ port: PORT });
const clients = new Map();
console.log(`🚀 WebRTC Signaling Server listening on ws://localhost:${PORT}`);
wss.on("connection", (ws) => {
    let registeredDeviceId = null;
    ws.on("message", (rawMessage) => {
        try {
            const message = JSON.parse(rawMessage.toString());
            switch (message.type) {
                case "REGISTER":
                    registeredDeviceId = message.senderDeviceId;
                    clients.set(registeredDeviceId, {
                        deviceId: registeredDeviceId,
                        clientType: message.payload?.clientType || "VIEWER",
                        ws
                    });
                    console.log(`[REGISTER] Client connected: ${registeredDeviceId} (${message.payload?.clientType})`);
                    ws.send(JSON.stringify({ type: "REGISTER_ACK", status: "SUCCESS", deviceId: registeredDeviceId }));
                    break;
                case "OFFER":
                case "ANSWER":
                case "ICE_CANDIDATE":
                case "STATUS_UPDATE":
                    if (!message.targetDeviceId) {
                        console.warn(`[WARN] Message type ${message.type} missing targetDeviceId`);
                        return;
                    }
                    const targetClient = clients.get(message.targetDeviceId);
                    if (targetClient && targetClient.ws.readyState === ws_1.WebSocket.OPEN) {
                        targetClient.ws.send(JSON.stringify(message));
                        console.log(`[RELAY] ${message.type} from ${message.senderDeviceId} -> ${message.targetDeviceId}`);
                    }
                    else {
                        console.warn(`[WARN] Target device ${message.targetDeviceId} offline`);
                        ws.send(JSON.stringify({
                            type: "ERROR",
                            message: `Target device ${message.targetDeviceId} is offline`
                        }));
                    }
                    break;
                case "HEARTBEAT":
                    ws.send(JSON.stringify({ type: "HEARTBEAT_ACK" }));
                    break;
                default:
                    console.warn(`[WARN] Unknown message type: ${message.type}`);
            }
        }
        catch (e) {
            console.error(`[ERROR] Parsing signaling message: ${e.message}`);
        }
    });
    ws.on("close", () => {
        if (registeredDeviceId) {
            clients.delete(registeredDeviceId);
            console.log(`[DISCONNECT] Client disconnected: ${registeredDeviceId}`);
        }
    });
});
