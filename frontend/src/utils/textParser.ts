export interface TextPart {
  type: 'text' | 'code' | 'artifact'
  content: string
  language?: string
  artifactType?: 'code' | 'json' | 'html' | 'form'
}

/**
 * 将消息正文拆成文本 / 代码块 / artifact 片段。
 * 对齐平台 Chat 契约：```lang、```artifact:*、`<schema>`。
 */
export function splitTextAndCodeBlocks(content: string): TextPart[] {
  if (!content) return [{ type: 'text', content: '' }]

  const parts: TextPart[] = []
  // 支持：```lang\n...\n```、```\n...\n```、单行 ```code```、以及 <schema>...</schema>
  const blockRegex =
    /```([^\n`]*)\r?\n([\s\S]*?)```|```([^\n`]*?)```|<schema>([\s\S]*?)<\/schema>/g

  let lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = blockRegex.exec(content)) !== null) {
    const before = content.slice(lastIndex, match.index)
    if (before) parts.push({ type: 'text', content: before })

    if (match[0].startsWith('<schema>')) {
      parts.push({
        type: 'code',
        content: (match[4] || '').trim(),
        language: 'json',
      })
    } else if (match[1] !== undefined) {
      // 多行围栏：```lang\ncode\n```
      const language = (match[1] || 'text').trim() || 'text'
      const code = (match[2] || '').replace(/^\n/, '').replace(/\n$/, '')
      pushFencedPart(parts, language, code)
    } else {
      // 单行围栏：```code```
      pushFencedPart(parts, 'text', (match[3] || '').trim())
    }

    lastIndex = match.index + match[0].length
  }

  const rest = content.slice(lastIndex)
  if (rest) parts.push({ type: 'text', content: rest })

  // 去掉首尾空文本段，但保留中间段落里的换行语义
  const normalized = parts.filter((p, i) => {
    if (p.type !== 'text') return true
    if (p.content.trim()) return true
    // 保留夹在两个非文本块之间的换行
    return i > 0 && i < parts.length - 1
  })

  return normalized.length ? normalized : [{ type: 'text', content }]
}

function pushFencedPart(parts: TextPart[], language: string, code: string) {
  if (language.startsWith('artifact:')) {
    const artifactType = language.slice('artifact:'.length) as TextPart['artifactType']
    parts.push({ type: 'artifact', content: code, language: artifactType, artifactType })
    return
  }
  parts.push({ type: 'code', content: code, language })
}

/**
 * 轻量 Markdown → HTML（文本段专用；代码块已由 split 拆出）。
 * 支持：转义、行内 code、粗体、标题、无序/有序列表、换行。
 */
export function renderMarkdown(content: string): string {
  if (!content) return ''

  let html = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 残留围栏兜底（未被 split 吃掉时）；语言标签做属性转义，避免 XSS
  html = html.replace(/```([^\n`]*)\r?\n([\s\S]*?)```/g, (_m, lang: string, code: string) => {
    const label = (lang || '')
      .trim()
      .replace(/[^a-zA-Z0-9_+#.-]/g, '')
      .slice(0, 32)
    const body = code.replace(/^\n/, '').replace(/\n$/, '')
    return `<pre><code data-lang="${label}">${body}</code></pre>`
  })

  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')

  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')

  // 连续无序列表 → 单个 <ul>
  html = html.replace(/(?:^|\n)((?:[-*] .+(?:\n|$))+)/g, (_m, block: string) => {
    const items = block
      .trim()
      .split(/\n/)
      .map((line: string) => line.replace(/^[-*] /, '').trim())
      .filter(Boolean)
      .map((item: string) => `<li>${item}</li>`)
      .join('')
    return `\n<ul>${items}</ul>\n`
  })

  // 连续有序列表 → 单个 <ol>
  html = html.replace(/(?:^|\n)((?:\d+\. .+(?:\n|$))+)/g, (_m, block: string) => {
    const items = block
      .trim()
      .split(/\n/)
      .map((line: string) => line.replace(/^\d+\. /, '').trim())
      .filter(Boolean)
      .map((item: string) => `<li>${item}</li>`)
      .join('')
    return `\n<ol>${items}</ol>\n`
  })

  html = html.replace(/\n/g, '<br>')
  html = html
    .replace(/<br>\s*(<(?:ul|ol|h[1-3]|pre)>)/g, '$1')
    .replace(/(<\/(?:ul|ol|h[1-3]|pre)>)\s*<br>/g, '$1')
    .replace(/(<(?:ul|ol)>)<br>/g, '$1')
    .replace(/<br>(<\/(?:ul|ol)>)/g, '$1')

  return html
}
