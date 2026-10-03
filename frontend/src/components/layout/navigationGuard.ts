import type { ViewId } from './routes'

export type NavigationGuard = (next: ViewId) => Promise<boolean>
export type RegisterNavigationGuard = (guard: NavigationGuard | null) => void
