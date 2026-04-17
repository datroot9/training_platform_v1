import { requestJson } from '../client'

export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await requestJson<Record<string, string>>('/api/account/change-password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}
