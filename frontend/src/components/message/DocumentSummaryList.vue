<script setup lang="ts">
import type { MessageDocumentSummary, MessageAttachment } from '../../types'

const props = defineProps<{
  summaries: MessageDocumentSummary[]
  /** 同消息的附件列表，用于匹配摘要 */
  attachments?: MessageAttachment[]
}>()

const emit = defineEmits<{
  (e: 'preview', attachment: MessageAttachment): void
}>()

/**
 * 匹配摘要对应的附件
 * 优先按 attachmentId（未来字段），否则按 filename 全等（忽略大小写）
 */
function findAttachment(doc: MessageDocumentSummary): MessageAttachment | null {
  if (!props.attachments?.length) return null

  // 未来字段：按 attachmentId 匹配
  if ((doc as any).attachmentId) {
    const found = props.attachments.find((a) => a.id === (doc as any).attachmentId)
    if (found) return found
  }

  // 按 filename 匹配（忽略大小写）
  const filename = (doc.filename || '').toLowerCase()
  const found = props.attachments.find((a) => (a.filename || '').toLowerCase() === filename)
  return found || null
}

/**
 * 点击摘要卡片
 */
function onDocClick(doc: MessageDocumentSummary) {
  const attachment = findAttachment(doc)
  if (attachment) {
    emit('preview', attachment)
  }
}

/**
 * 摘要卡片是否可点击（有对应附件）
 */
function isClickable(doc: MessageDocumentSummary): boolean {
  return findAttachment(doc) !== null
}
</script>

<template>
  <div v-if="summaries?.length" class="doc-list">
    <article
      v-for="doc in summaries"
      :key="doc.documentId"
      class="doc-card"
      :class="{ clickable: isClickable(doc) }"
      :role="isClickable(doc) ? 'button' : undefined"
      :tabindex="isClickable(doc) ? 0 : undefined"
      :aria-label="isClickable(doc) ? `预览 ${doc.filename}` : undefined"
      @click="onDocClick(doc)"
      @keydown.enter="onDocClick(doc)"
      @keydown.space.prevent="onDocClick(doc)"
    >
      <div class="doc-icon" aria-hidden="true">
        <svg viewBox="0 0 16 16" width="14" height="14">
          <path d="M4 2.5h5.2L12 5.3V13.5H4V2.5Z" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
          <path d="M9.2 2.5V5.3H12" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
        </svg>
      </div>
      <div class="doc-body">
        <strong class="doc-name">{{ doc.filename }}</strong>
        <p class="doc-summary">{{ doc.summary }}</p>
        <span v-if="doc.pageCount" class="doc-meta">{{ doc.pageCount }} 页</span>
        <span v-if="!isClickable(doc)" class="doc-hint">无对应附件，请从附件列表打开</span>
      </div>
    </article>
  </div>
</template>

<style scoped>
.doc-list { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.doc-card {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: #f7fafa;
}
.doc-card.clickable {
  cursor: pointer;
  transition: border-color .15s ease, box-shadow .15s ease;
}
.doc-card.clickable:hover {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 2px rgba(13, 107, 103, .08);
}
.doc-card.clickable:focus-visible {
  outline: 2px solid var(--c-primary);
  outline-offset: 2px;
}
.doc-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--c-primary-soft);
  color: var(--c-primary);
}
.doc-body { min-width: 0; flex: 1; }
.doc-name { display: block; font-size: 13px; font-weight: 650; }
.doc-summary { margin: 4px 0 0; font-size: 12px; line-height: 1.5; color: var(--c-text-secondary); }
.doc-meta { display: inline-block; margin-top: 4px; font-size: 11px; color: var(--c-text-muted); }
.doc-hint {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--c-text-muted);
  font-style: italic;
}
</style>
