import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '@/store/AuthStore'

let stompClient: Client | null = null

export const connectWebSocket = (
  onQueueUpdate: (data: unknown) => void,
  branchId: string,
  userId: string
) => {
  const token = useAuthStore.getState().token

  stompClient = new Client({
    // SockJS fallback for browsers that don't support raw WS
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: { Authorization: `Bearer ${token}` },
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