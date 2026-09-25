import { request } from './http'

export type Account = { id: string; username: string; role: 'ADMIN' | 'USER'; enabled: boolean; createdAt: string }

export const getCurrentAccount = () => request<Account>('/api/auth/me')
export const login = (username: string, password: string) => request<Account>('/api/auth/login', {
  method: 'POST', body: JSON.stringify({ username, password }),
})
export const logout = () => request<void>('/api/auth/logout', { method: 'POST' })
export const signalActivity = () => request<{ active: boolean }>('/api/auth/activity', { method: 'POST' })
export const changeOwnPassword = (currentPassword: string, newPassword: string) => request<Account>('/api/auth/password', {
  method: 'POST', body: JSON.stringify({ currentPassword, newPassword }),
})
export const listAccounts = () => request<Account[]>('/api/admin/users')
export const createAccount = (username: string, password: string, role: Account['role']) => request<Account>('/api/admin/users', {
  method: 'POST', body: JSON.stringify({ username, password, role }),
})
export const changeAccountRole = (id: string, role: Account['role']) => request<Account>(`/api/admin/users/${id}/role`, {
  method: 'PATCH', body: JSON.stringify({ role }),
})
export const changeAccountEnabled = (id: string, enabled: boolean) => request<Account>(`/api/admin/users/${id}/enabled`, {
  method: 'PATCH', body: JSON.stringify({ enabled }),
})
export const resetAccountPassword = (id: string, password: string) => request<Account>(`/api/admin/users/${id}/reset-password`, {
  method: 'POST', body: JSON.stringify({ password }),
})
