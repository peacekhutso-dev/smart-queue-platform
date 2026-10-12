import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

let stompClient: Client | null = null

export const connectWebSocket = (
  onQueueUpdate: (data: unknown) => void,
  branchId: string,
  userId: string
) => {
  // No more manually reading a token to set as a STOMP CONNECT header — the
  // JWT lives in an httpOnly dha_token cookie now, which the browser attaches
  // automatically to the SockJS handshake request. queue-service establishes
  // identity from that cookie during the handshake itself (see
  // HandshakeAuthInterceptor) rather than from this connection's STOMP frames.
  stompClient = new Client({
    // SockJS fallback for browsers that don't support raw WS. Native
    // WebSocket (SockJS's preferred transport in any modern browser) always
    // attaches cookies matching the target's domain automatically, same as
    // a plain navigation — no separate credentials flag needed there.
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 5000,

    onConnect: () => {
      console.log('[WS] Connected to DHA Queue WebSocket')

      // Subscribe to branch-wide queue updates
      stompClient?.subscribe(`/topic/branch/${branchId}/queue`, (msg) => {
        onQueueUpdate(JSON.parse(msg.body))
      })

      // Subscribe to personal status updates
      stompClient?.subscribe(`/topic/user/${userId}/status`, (msg) => {
        onQueueUpdate(JSON.parse(msg.body))
      })
    },

    onDisconnect: () => console.log('[WS] Disconnected'),
    onStompError: (frame) => console.error('[WS] STOMP error:', frame),
  })

  stompClient.activate()
}

export const disconnectWebSocket = () => {
  stompClient?.deactivate()
  stompClient = null
}
