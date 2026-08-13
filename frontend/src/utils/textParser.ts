export interface TextPart {
  type: 'text' | 'code' | 'artifact'
  content: string
  language?: string
  artifactType?: 'code' | 'json' | 'html' | 'form'
}

/** Keep message rendering aligned with the platform chat message contract. */
export function splitTextAndCodeBlocks(content: string): TextPart[] {
  const parts: TextPart[] = []
  const blockRegex = /(<schema>[\s\S]*?<\/schema>|```([\w:]+)?\n([\s\S]*?)```)/g
  let lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = blockRegex.exec(content)) !== null) {
    const before = content.slice(lastIndex, match.index)
    if (before.trim()) parts.push({ type: 'text', content: before })
    const full = match[0]
    if (full.startsWith('<schema>')) {
      parts.push({ type: 'code', content: full.replace(/<\/?schema>/g, '').trim(), language: 'json' })
    } else {
      const language = match[2] || 'text'
      const code = match[3].trim()
      if (language.startsWith('artifact:')) {
        const artifactType = language.slice('artifact:'.length) as TextPart['artifactType']
        parts.push({ type: 'artifact', content: code, language: artifactType, artifactType })
      } else parts.push({ type: 'code', content: code, language })
    }
    lastIndex = match.index + full.length
  }
  const rest = content.slice(lastIndex)
  if (rest.trim()) parts.push({ type: 'text', content: rest })
  return parts.length ? parts : [{ type: 'text', content }]
}
