export type UserSearchResult = {
  avatarUrl: null | string
  displayName: string
  email: string
  id: string
}

export type UserSearchResponse = {
  items: UserSearchResult[]
}
