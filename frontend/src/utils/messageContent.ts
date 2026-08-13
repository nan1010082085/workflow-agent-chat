/**
 * 助手消息正文规范化：过滤节点 JSON dump，保证气泡可读。
 */

/**
 * 是否为平台节点结构化 dump（不应直接当 Markdown 展示）。
 * @param {string} content
 * @returns {boolean}
 */
export function isNodeJsonDump(content: string): boolean {
  const t = (content || '').trim()
  if (!t.startsWith('{') || !t.endsWith('}')) return false
  try {
    const obj = JSON.parse(t) as Record<string, unknown>
    if (!obj || typeof obj !== 'object') return false
    return (
      'confirmQuestions' in obj
      || 'completeness' in obj
      || 'recommendedExperts' in obj
      || 'routeReason' in obj
      || ('intent' in obj && !('text' in obj && String(obj.text || '').trim()))
    )
  } catch {
    return false
  }
}

/**
 * 把需求分析 JSON 转成可读 Markdown。
 * @param {string} content
 * @returns {string}
 */
export function formatAnalyzerDump(content: string): string {
  try {
    const obj = JSON.parse(content.trim()) as {
      intent?: string
      completeness?: number | string
      confirmQuestions?: Array<string | { question?: string; label?: string }>
    }
    const lines: string[] = ['## 需求理解', '']
    if (obj.intent) lines.push(`- 意图：${obj.intent}`)
    if (obj.completeness != null && String(obj.completeness).trim() !== '') {
      lines.push(`- 完整度：${obj.completeness}%`)
    }
    const qs = (obj.confirmQuestions || [])
      .map((q) => (typeof q === 'string' ? q : q.question || q.label || ''))
      .map((q) => q.trim())
      .filter(Boolean)
    if (qs.length) {
      lines.push('', '## 需要你补充', '')
      qs.forEach((q, i) => lines.push(`${i + 1}. ${q}`))
    }
    return lines.join('\n').trim()
  } catch {
    return ''
  }
}

/**
 * 供气泡渲染的正文：优先可读文本，JSON dump 自动转写。
 * @param {string | null | undefined} content
 * @returns {string}
 */
export function normalizeAssistantContent(content: string | null | undefined): string {
  const raw = (content || '').trim()
  if (!raw) return ''
  if (!isNodeJsonDump(raw)) return content || ''
  return formatAnalyzerDump(raw) || ''
}

/**
 * 正文是否已包含「需要补充」类提问区块（避免确认卡重复罗列）。
 * @param {string} content
 * @returns {boolean}
 */
export function contentHasQuestionSection(content: string): boolean {
  return /##\s*(需要你补充|确认项|待确认)/.test(content || '')
}
