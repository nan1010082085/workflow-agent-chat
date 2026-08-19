/**
 * 附件类型判断工具函数
 * 从 MessageAttachmentList.vue 迁出，供 AttachmentPreviewModal 和其他组件复用
 */

import type { MessageAttachment } from '../types'

/**
 * 是否为图片 MIME，用于内联预览。
 * @param {MessageAttachment} att
 */
export function isImage(att: MessageAttachment): boolean {
  const mime = (att.mimetype || '').toLowerCase()
  const name = (att.filename || '').toLowerCase()
  return mime.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp)$/.test(name)
}

/**
 * 是否为可 iframe 预览的 PDF。
 * @param {MessageAttachment} att
 */
export function isPdf(att: MessageAttachment): boolean {
  const mime = (att.mimetype || '').toLowerCase()
  const name = (att.filename || '').toLowerCase()
  return mime.includes('pdf') || name.endsWith('.pdf')
}

/**
 * 是否为 Office 文档（Word/Excel）。
 * @param {MessageAttachment} att
 */
export function isOffice(att: MessageAttachment): boolean {
  const mime = (att.mimetype || '').toLowerCase()
  const name = (att.filename || '').toLowerCase()
  return (
    name.endsWith('.docx') ||
    name.endsWith('.doc') ||
    name.endsWith('.xlsx') ||
    name.endsWith('.xls') ||
    mime.includes('word') ||
    mime.includes('sheet') ||
    mime.includes('excel')
  )
}

/**
 * 是否为可应用内预览的类型（图片或 PDF）。
 * @param {MessageAttachment} att
 */
export function isPreviewable(att: MessageAttachment): boolean {
  return isImage(att) || isPdf(att)
}

/**
 * 文件类型短标签。
 * @param {MessageAttachment} att
 */
export function fileKind(att: MessageAttachment): string {
  const name = (att.filename || '').toLowerCase()
  const mime = (att.mimetype || '').toLowerCase()
  if (isPdf(att)) return 'PDF'
  if (name.endsWith('.docx') || name.endsWith('.doc') || mime.includes('word')) return 'Word'
  if (name.endsWith('.xlsx') || name.endsWith('.xls') || mime.includes('sheet') || mime.includes('excel')) return 'Excel'
  if (name.endsWith('.csv')) return 'CSV'
  if (name.endsWith('.txt') || name.endsWith('.md')) return '文本'
  if (name.endsWith('.json')) return 'JSON'
  return '文件'
}

/**
 * 人类可读文件大小。
 * @param {number} [size]
 */
export function formatSize(size?: number): string {
  if (size == null || size < 0) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}
