import { marked } from 'marked'
import purify from 'dompurify'

/** ESM/CJS 互操作：拿到带 sanitize 的 DOMPurify 实例。 */
const DOMPurify = (
  typeof (purify as { sanitize?: unknown }).sanitize === 'function'
    ? purify
    : (purify as unknown as { default: typeof purify }).default
)

export interface TextPart {
  type: 'text' | 'code' | 'artifact'
  content: string
  language?: string
  artifactType?: 'code' | 'json' | 'html' | 'form'
}

/**
 * 判断 schema 标签后是否为 LLM 多余总结（对齐 ai/app textParser）。
 */
function isRedundantSummary(text: string): boolean {
  const trimmed = text.trim()
  if (!trimmed) return false
  const patterns = [
    /^(好的|已|我|现在|以上|这就是|这是|根据|基于)/,
    /^(表单|流程|Schema|JSON|数据)\s*(已|已经|已生成|已创建|已更新)/,
    /已(生成|创建|更新|完成|应用)(好|了)?/,
    /以上(就是|是)/,
    /请(查看|确认|检查|参考)/,
    /希望(这|这个)/,
  ]
  return trimmed.length < 100 && patterns.some((p) => p.test(trimmed))
}

/**
 * 将消息正文拆成文本 / 代码块 / artifact 片段。
 * 对齐平台 Chat 契约：```lang、```artifact:*、`<schema>`。
 */
export function splitTextAndCodeBlocks(content: string): TextPart[] {
  if (!content) return [{ type: 'text', content: '' }]

  const parts: TextPart[] = []
  // 多行围栏 / 单行围栏 / schema（语言支持 word:sub 形式，对齐 ai/app）
  const blockRegex =
    /```([\w:+#.-]*)\r?\n([\s\S]*?)```|```([^\n`]*?)```|<schema>([\s\S]*?)<\/schema>/g

  let lastIndex = 0
  let match: RegExpExecArray | null
  let hasSchemaTag = false

  while ((match = blockRegex.exec(content)) !== null) {
    const before = content.slice(lastIndex, match.index)
    if (before.trim()) parts.push({ type: 'text', content: before })

    if (match[0].startsWith('<schema>')) {
      parts.push({
        type: 'code',
        content: (match[4] || '').trim(),
        language: 'json',
      })
      hasSchemaTag = true
    } else if (match[1] !== undefined) {
      const language = (match[1] || 'text').trim() || 'text'
      const code = (match[2] || '').replace(/^\n/, '').replace(/\n$/, '')
      pushFencedPart(parts, language, code)
    } else {
      pushFencedPart(parts, 'text', (match[3] || '').trim())
    }

    lastIndex = match.index + match[0].length
  }

  const rest = content.slice(lastIndex)
  if (rest.trim()) {
    if (hasSchemaTag && isRedundantSummary(rest)) {
      // 过滤 schema 后的冗余总结
    } else {
      parts.push({ type: 'text', content: rest })
    }
  }

  const normalized = parts.filter((p, i) => {
    if (p.type !== 'text') return true
    if (p.content.trim()) return true
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
 * Markdown → 安全 HTML（对齐 ai/app TextRenderer：marked + DOMPurify）。
 * DOMPurify 依赖 window；无 DOM 环境时仅返回 marked 结果（构建期/测试兜底）。
 */
export function renderMarkdown(content: string): string {
  if (!content) return ''
  const rawHtml = marked.parse(content, { breaks: true, gfm: true }) as string
  const wrapped = rawHtml
    .replace(/<table>/g, '<div class="table-scroll"><table>')
    .replace(/<\/table>/g, '</table></div>')
  if (typeof window === 'undefined' || typeof DOMPurify?.sanitize !== 'function') {
    return wrapped
  }
  return DOMPurify.sanitize(wrapped, { ADD_ATTR: ['class'] })
}
