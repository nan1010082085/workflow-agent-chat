<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MessageAttachment } from '../../types'
import { attachmentContentUrl } from '../../api/client'

const props = defineProps<{ attachments: MessageAttachment[] }>()

const preview = ref<MessageAttachment | null>(null)

const images = computed(() => props.attachments.filter((a) => isImage(a)))
const files = computed(() => props.attachments.filter((a) => !isImage(a)))

/**
 * 是否为图片 MIME，用于内联预览。
 */
function isImage(att: MessageAttachment): boolean {
  return (att.mimetype || '').startsWith('image/')
}

/**
 * 人类可读文件大小。
 */
function formatSize(size?: number): string {
  if (size == null || size < 0) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function openPreview(att: MessageAttachment) {
  preview.value = att
}

function closePreview() {
  preview.value = null
}
</script>

<template>
  <div v-if="attachments?.length" class="att-list">
    <div v-if="images.length" class="image-grid" :class="{ multi: images.length > 1 }">
      <button
        v-for="att in images"
        :key="att.id"
        type="button"
        class="att-image"
        @click="openPreview(att)"
      >
        <img :src="attachmentContentUrl(att)" :alt="att.filename" loading="lazy" />
      </button>
    </div>

    <a
      v-for="att in files"
      :key="att.id"
      class="att-file"
      :href="attachmentContentUrl(att)"
      target="_blank"
      rel="noopener"
    >
      <span class="att-icon" aria-hidden="true">
        <svg viewBox="0 0 16 16" width="14" height="14">
          <path d="M9.2 2.8 4.4 7.6a2.6 2.6 0 0 0 3.7 3.7l5.2-5.2a1.8 1.8 0 0 0-2.5-2.5L5.6 8.8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </span>
      <span class="att-meta">
        <strong>{{ att.filename }}</strong>
        <small v-if="att.size">{{ formatSize(att.size) }}</small>
        <em v-if="att.excerpt">{{ att.excerpt }}</em>
      </span>
    </a>
  </div>

  <teleport to="body">
    <div v-if="preview" class="preview-mask" @click="closePreview">
      <div class="preview-panel" @click.stop>
        <header class="preview-head">
          <strong>{{ preview.filename }}</strong>
          <div class="preview-actions">
            <a :href="attachmentContentUrl(preview)" target="_blank" rel="noopener">打开原图</a>
            <button type="button" @click="closePreview">关闭</button>
          </div>
        </header>
        <img :src="attachmentContentUrl(preview)" :alt="preview.filename" />
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.att-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}
.image-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(0, 1fr);
}
.image-grid.multi {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.att-image {
  display: block;
  width: 100%;
  padding: 0;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  overflow: hidden;
  background: #f7fafa;
  cursor: zoom-in;
}
.att-image img {
  display: block;
  width: 100%;
  max-height: 240px;
  object-fit: cover;
  background: #fff;
}
.att-file {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: #f7fafa;
  text-decoration: none;
  color: inherit;
}
.att-file:hover { border-color: var(--c-primary); }
.att-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--c-primary-soft);
  color: var(--c-primary);
}
.att-meta { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 2px; }
.att-meta strong { font-size: 13px; font-weight: 650; word-break: break-all; }
.att-meta small { font-size: 11px; color: var(--c-text-muted); }
.att-meta em {
  font-style: normal;
  font-size: 12px;
  line-height: 1.45;
  color: var(--c-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.preview-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(16, 24, 32, .55);
}
.preview-panel {
  width: min(920px, 100%);
  max-height: 90vh;
  overflow: auto;
  border-radius: 12px;
  background: #111;
  box-shadow: 0 20px 48px rgba(0,0,0,.35);
}
.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  color: #fff;
  background: #1b232b;
}
.preview-head strong { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.preview-actions { display: flex; gap: 8px; flex: none; }
.preview-actions a,
.preview-actions button {
  border: 0;
  border-radius: 6px;
  padding: 6px 10px;
  background: rgba(255,255,255,.1);
  color: #fff;
  font-size: 12px;
  text-decoration: none;
  cursor: pointer;
}
.preview-panel img {
  display: block;
  width: 100%;
  max-height: calc(90vh - 52px);
  object-fit: contain;
  background: #000;
}
</style>
