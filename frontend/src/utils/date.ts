export const WORKBENCH_TIME_ZONE = 'Asia/Shanghai'
const workbenchParts = (date = new Date()) => Object.fromEntries(
  new Intl.DateTimeFormat('en-CA', {
    timeZone: WORKBENCH_TIME_ZONE,
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(date).map(({ type, value }) => [type, value]),
)
export const localDate = (date = new Date()) => {
  const parts = workbenchParts(date)
  return `${parts.year}-${parts.month}-${parts.day}`
}
export const localDateTime = (iso?: string) => {
  const parts = workbenchParts(iso ? new Date(iso) : new Date())
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}
export const timeForDate = (date: string) => `${date}T${localDateTime().slice(11)}`
export const toInstant = (dateTime: string) => new Date(`${dateTime}:00+08:00`).toISOString()
export const weekStart = (date: string) => {
  const selected = new Date(`${date}T12:00:00+08:00`)
  const day = selected.getUTCDay() || 7
  selected.setUTCDate(selected.getUTCDate() - day + 1)
  return localDate(selected)
}
export const weekLabel = (date: string) => {
  const monday = new Date(`${weekStart(date)}T12:00:00+08:00`)
  const sunday = new Date(monday); sunday.setUTCDate(monday.getUTCDate() + 6)
  const format = (value: Date) => new Intl.DateTimeFormat('zh-CN', { timeZone: WORKBENCH_TIME_ZONE, month: 'long', day: 'numeric' }).format(value)
  return `${format(monday)}—${format(sunday)}（周一至周日）`
}
