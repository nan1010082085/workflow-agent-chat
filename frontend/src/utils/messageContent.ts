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
 * 把平台 Runtime 技术错误转成用户可读说明。
 * @param {string} content
 * @returns {string}
 */
export function humanizeRuntimeError(content: string): string {
  const t = (content || '').trim()
  if (!t) return ''
  if (/未指定上传文件流|未指定图片上传流|\$input\.file|请上传文件|请上传图片/.test(t)) {
    return [
      '## 需要文档或图片',
      '',
      '这个助手需要你提供待处理的内容。你可以：',
      '',
      '1. **粘贴正文**（合同、纪要、简历等）后发送',
      '2. **上传附件**（txt / pdf / 图片）后再试',
      '',
      '若刚粘贴过仍失败，请新建对话重试，或换用支持文件的助手。',
    ].join('\n')
  }
  if (/上传流不是图片类型/.test(t)) {
    return [
      '## 需要图片附件',
      '',
      '当前助手按图片识别处理。请上传 PNG / JPG 等图片，或改用文档类助手处理纯文本。',
    ].join('\n')
  }
  if (/"approved"\s*:\s*true/.test(t) && /"comment"\s*:/.test(t) && t.length < 280) {
    return '已确认。若结果未更新，请稍候刷新，或继续追问以查看完整分析。'
  }
  if (/Workflow not found|document-parse/i.test(t) && t.length < 120) {
    return '助手暂时不可用，请稍后重试或换一个助手。'
  }
  return content
}

/**
 * 供气泡渲染的正文：优先可读文本，JSON dump / 技术错误自动转写。
 * @param {string | null | undefined} content
 * @returns {string}
 */
export function normalizeAssistantContent(content: string | null | undefined): string {
  const raw = (content || '').trim()
  if (!raw) return ''
  if (isNodeJsonDump(raw)) return formatAnalyzerDump(raw) || ''
  return humanizeRuntimeError(content || '')
}

/**
 * 正文是否已包含「需要补充」类提问区块（避免确认卡重复罗列）。
 * @param {string} content
 * @returns {boolean}
 */
export function contentHasQuestionSection(content: string): boolean {
  return /##\s*(需要你补充|确认项|待确认|需要文档或图片)/.test(content || '')
}
