export type ClientCommandEnvelope = {
  payload: unknown
  requestId: string
  type: string
}

export type ServerErrorEnvelope = {
  code?: string
  message?: string
  requestId?: string
  type: 'error'
}

export type PersistedEventEnvelope = {
  eventSeq: number
  eventType: string
  payload: unknown
  type: 'event'
}

export type EphemeralEventEnvelope = {
  payload: unknown
  requestId?: string
  type: string
}

export type ServerEnvelope =
  | EphemeralEventEnvelope
  | PersistedEventEnvelope
  | ServerErrorEnvelope
