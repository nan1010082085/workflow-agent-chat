<script setup lang="ts">
/**
 * 应用标记：与 public/favicon.svg / favicon.ico 同源。
 * 一律为青绿圆角方 + 白色描边 W；不再使用橙底星形。
 */
const props = withDefaults(
  defineProps<{
    /** 保留 API；视觉与 ico 一致，不再区分橙星 */
    variant?: 'product' | 'ai'
    size?: 'sm' | 'md' | 'lg'
    /**
     * 自定义字形；空 / ✦ / ★ / W 时使用与 favicon 相同的描边 W。
     */
    glyph?: string
  }>(),
  {
    variant: 'product',
    size: 'md',
    glyph: '',
  },
)

/**
 * 是否渲染自定义字形（否则走 favicon 同源 W）。
 */
function hasCustomGlyph(value: string): boolean {
  return Boolean(value) && !['✦', '★', 'W', 'w'].includes(value)
}
</script>

<template>
  <span
    class="app-mark"
    :class="[`is-${props.size}`]"
    aria-hidden="true"
  >
    <template v-if="hasCustomGlyph(props.glyph)">{{ props.glyph }}</template>
    <!-- 路径与 frontend/public/favicon.svg 保持一致 -->
    <svg v-else class="mark-svg" viewBox="0 0 32 32" focusable="false">
      <path
        d="M7 9 L11.5 20 L16 12.5 L20.5 20 L25 9"
        fill="none"
        stroke="currentColor"
        stroke-width="2.6"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
  </span>
</template>

<style scoped>
.app-mark {
  display: grid;
  place-items: center;
  flex: none;
  color: #fff;
  background: var(--c-primary);
  border-radius: 22%;
  line-height: 1;
  font-weight: 800;
  user-select: none;
  overflow: hidden;
}
.app-mark.is-sm {
  width: 28px;
  height: 28px;
  font-size: 13px;
}
.app-mark.is-md {
  width: 34px;
  height: 34px;
  font-size: 15px;
}
.app-mark.is-lg {
  width: 56px;
  height: 56px;
  font-size: 24px;
  border-radius: 12px;
}
.mark-svg {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
