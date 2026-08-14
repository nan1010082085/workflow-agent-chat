import { describe, expect, it } from 'vitest'
import {
  contentHasQuestionSection,
  formatAnalyzerDump,
  humanizeRuntimeError,
  isNodeJsonDump,
  normalizeAssistantContent,
} from '../src/utils/messageContent'

describe('messageContent', () => {
  it('detects analyzer json dump', () => {
    const raw = JSON.stringify({
      intent: 'help',
      completeness: 20,
      confirmQuestions: ['请描述需求'],
    })
    expect(isNodeJsonDump(raw)).toBe(true)
  })

  it('formats analyzer dump to markdown', () => {
    const raw = JSON.stringify({
      intent: 'help',
      completeness: 20,
      confirmQuestions: ['请描述需求', '业务场景是什么'],
    })
    const md = formatAnalyzerDump(raw)
    expect(md).toContain('## 需求理解')
    expect(md).toContain('## 需要你补充')
    expect(md).toContain('请描述需求')
  })

  it('normalizes dump to readable content', () => {
    const raw = JSON.stringify({
      intent: 'help',
      completeness: 10,
      confirmQuestions: ['补一句'],
    })
    const out = normalizeAssistantContent(raw)
    expect(out.startsWith('{')).toBe(false)
    expect(out).toContain('补一句')
  })

  it('keeps normal prose', () => {
    expect(normalizeAssistantContent('你好，这是正文')).toBe('你好，这是正文')
  })

  it('detects question sections', () => {
    expect(contentHasQuestionSection('## 需要你补充\n\n1. a')).toBe(true)
    expect(contentHasQuestionSection('普通回复')).toBe(false)
  })

  it('humanizes missing file stream errors', () => {
    const raw = '未指定上传文件流（$input.file）。请上传文件，或在 Chat 中附加附件后触发。'
    const out = humanizeRuntimeError(raw)
    expect(out).toContain('需要文档或图片')
    expect(out).not.toContain('$input.file')
    expect(normalizeAssistantContent(raw)).toContain('粘贴正文')
  })
})
