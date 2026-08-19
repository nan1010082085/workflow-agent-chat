<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MessageAttachment } from '../../types'
import { attachmentContentUrl } from '../../api/client'
import { isImage, isPdf, isOffice, fileKind, formatSize } from '../../utils/attachmentKind'
import AttachmentPreviewModal from './AttachmentPreviewModal.vue'

const props = defineProps<{ attachments: MessageAttachment[] }>()

const previewOpen = ref(false)
const previewAttachment = ref<MessageAttachment | null>(null)

const images = computed(() => props.attachments.filter((a) => isImage(a)))
const files = computed(() => props.attachments.filter((a) => !isImage(a)))

/**
 * 打开预览：图片、PDF 和 Office 文档进统一预览壳。
 * @param {MessageAttachment} att
 */
function openPreview(att: MessageAttachment) {
  previewAttachment.value = att
  previewOpen.value = true
}

function closePreview() {
  previewOpen.value = false
  previewAttachment.value = null
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
      :aria-label="`${isPdf(att) ? '预览' : isOffice(att) ? '查看' : '打开'} ${att.filename}`"
      @click="openPreview(att)"
    >
      <span class="att-badge" aria-hidden="true">{{ fileKind(att) }}</span>
      <span class="att-meta">
        <strong>{{ att.filename }}</strong>
        <small>
          <template v-if="att.size">{{ formatSize(att.size) }}</template>
          <template v-if="isPdf(att)"> · 点击预览</template>
          <template v-else-if="isOffice(att)"> · 点击查看</template>
          <template v-else> · 点击打开/下载</template>
        </small>
        <em v-if="att.excerpt">{{ att.excerpt }}</em>
      </span>
    </button>
  </div>

  <AttachmentPreviewModal
    v-model="previewOpen"
    :attachment="previewAttachment"
    :gallery="attachments"
  />
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
</style>
