type WebSocketClientHandlers = {
  onClose?: (event: CloseEvent) => void
  onError?: (event: Event) => void
  onMessage?: (event: MessageEvent<string>) => void
  onOpen?: (event: Event) => void
}

export class WebSocketClient {
  private socket: WebSocket | null = null

  close() {
    this.socket?.close()
    this.socket = null
  }

  connect(url: string, handlers: WebSocketClientHandlers = {}) {
    this.close()

    const socket = new WebSocket(url)
    this.socket = socket

    socket.onclose = (event) => handlers.onClose?.(event)
    socket.onerror = (event) => handlers.onError?.(event)
    socket.onmessage = (event) =>
      handlers.onMessage?.(event as MessageEvent<string>)
    socket.onopen = (event) => handlers.onOpen?.(event)

    return socket
  }

  isConnected() {
    return this.socket?.readyState === WebSocket.OPEN
  }

  sendJson(object: unknown) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not connected.')
    }

    this.socket.send(JSON.stringify(object))
  }
}
