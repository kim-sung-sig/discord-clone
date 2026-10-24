// Author label shown for a chat message. Never falls back to a raw user id.
export const UNKNOWN_MESSAGE_AUTHOR = '알 수 없는 사용자'

export interface MessageAuthorFields {
  authorId?: string | null
  authorDisplayName?: string | null
}

export interface MessageAuthorViewer {
  id: string
  username?: string | null
  displayName?: string | null
}

const nonBlank = (value: string | null | undefined): string | undefined => {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

export const messageAuthorLabel = (
  author: MessageAuthorFields,
  viewer?: MessageAuthorViewer | null,
  knownLabel?: string
): string => {
  const displayName = nonBlank(author.authorDisplayName)
  if (displayName) {
    return displayName
  }
  if (viewer && author.authorId && author.authorId === viewer.id) {
    const viewerName = nonBlank(viewer.displayName) ?? nonBlank(viewer.username)
    if (viewerName) {
      return viewerName
    }
  }
  return nonBlank(knownLabel) ?? UNKNOWN_MESSAGE_AUTHOR
}

export const messageAuthorInitials = (name: string): string => {
  const characters = Array.from(name.trim())
  return characters.length ? characters.slice(0, 2).join('').toUpperCase() : '?'
}
