import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { QueueBroadcast } from '../types'

let stompClient: Client | null = null

export const connectBranchQueue = (
  branchId: number,
  onUpdate: (broadcast: QueueBroadcast) => void,
) => {
  // The JWT lives in an httpOnly dha_token cookie now, which the browser
  // attaches automatically to the SockJS handshake request — no more manually
  // reading a token to set as a STOMP CONNECT header.
  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 5000,

    onConnect: () => {
      stompClient?.subscribe(`/topic/branch/${branchId}/queue`, (msg) => {
        onUpdate(JSON.parse(msg.body))
      })
    },

    onStompError: (frame) => console.error('[WS] STOMP error:', frame),
  })

  stompClient.activate()
}

export const disconnectBranchQueue = () => {
  stompClient?.deactivate()
  stompClient = null
}
