import type { LocalOutboxCommand } from '@/db/models'
import { sendCommand } from '@/features/realtime/commandSender'

export function sendOutboxCommand(command: LocalOutboxCommand) {
  sendCommand(command.commandType, command.payload, command.id)
}
