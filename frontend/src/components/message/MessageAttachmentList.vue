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
 * @param {MessageAttachment} att
 */
function isImage(att: MessageAttachment): boolean {
  const mime = (att.mimetype || '').toLowerCase()
  const name = (att.filename || '').toLowerCase()
  return mime.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp)$/.test(name)
}

/**
 * 是否为可 iframe 预览的 PDF。
 * @param {MessageAttachment} att
 */
function isPdf(att: MessageAttachment): boolean {
  const mime = (att.mimetype || '').toLowerCase()
  const name = (att.filename || '').toLowerCase()
  return mime.includes('pdf') || name.endsWith('.pdf')
}

/**
 * 文件类型短标签。
 * @param {MessageAttachment} att
 */
function fileKind(att: MessageAttachment): string {
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
function formatSize(size?: number): string {
  if (size == null || size < 0) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * 打开预览：图片与 PDF 进弹层，其它类型新标签下载/打开。
 * @param {MessageAttachment} att
 */
function openPreview(att: MessageAttachment) {
  if (isImage(att) || isPdf(att)) {
    preview.value = att
    return
  }
  window.open(attachmentContentUrl(att), '_blank', 'noopener')
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
        :aria-label="`预览图片 ${att.filename}`"
        @click="openPreview(att)"
      >
        <img :src="attachmentContentUrl(att)" :alt="att.filename" loading="lazy" />
      </button>
    </div>

    <button
      v-for="att in files"
      :key="att.id"
      type="button"
      class="att-file"
      :aria-label="`${isPdf(att) ? '预览' : '打开'} ${att.filename}`"
      @click="openPreview(att)"
    >
      <span class="att-badge" aria-hidden="true">{{ fileKind(att) }}</span>
      <span class="att-meta">
        <strong>{{ att.filename }}</strong>
        <small>
          <template v-if="att.size">{{ formatSize(att.size) }}</template>
          <template v-if="isPdf(att)"> · 点击预览</template>
          <template v-else> · 点击打开/下载</template>
        </small>
        <em v-if="att.excerpt">{{ att.excerpt }}</em>
      </span>
    </button>
  </div>

  <teleport to="body">
    <div v-if="preview" class="preview-mask" @click="closePreview">
      <div class="preview-panel" :class="{ pdf: isPdf(preview) }" @click.stop>
        <header class="preview-head">
          <strong>{{ preview.filename }}</strong>
          <div class="preview-actions">
            <a :href="attachmentContentUrl(preview)" target="_blank" rel="noopener">
              {{ isPdf(preview) ? '新窗口打开' : '打开原图' }}
            </a>
            <button type="button" @click="closePreview">关闭</button>
          </div>
        </header>
        <img
          v-if="isImage(preview)"
          :src="attachmentContentUrl(preview)"
          :alt="preview.filename"
        />
        <iframe
          v-else-if="isPdf(preview)"
          class="pdf-frame"
          :src="attachmentContentUrl(preview)"
          :title="preview.filename"
        />
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
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: #f7fafa;
  text-align: left;
  color: inherit;
  cursor: pointer;
  font: inherit;
}
.att-file:hover { border-color: var(--c-primary); }
.att-badge {
  flex: none;
  min-width: 40px;
  height: 28px;
  padding: 0 8px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--c-primary-soft);
  color: var(--c-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
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
.preview-panel.pdf {
  width: min(1040px, 100%);
  height: min(90vh, 860px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
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
.pdf-frame {
  flex: 1;
  width: 100%;
  border: 0;
  background: #fff;
}
</style>
