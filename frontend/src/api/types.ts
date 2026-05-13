export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface PageQuery {
  page: number
  size: number
}
