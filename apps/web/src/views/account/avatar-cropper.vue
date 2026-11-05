<script setup lang="ts">
import { ElButton, ElMessage, ElUpload, type UploadFile } from 'element-plus'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { VueCropper } from 'vue-cropper/dist/vue-cropper.es.js'
import 'vue-cropper/dist/index.css'

import { useUploadAvatar } from '@/api/profile'

defineOptions({ name: 'AccountAvatarCropper' })

/**
 * 头像裁剪弹窗：选图（页内真实文件，不自动上传）→ vue-cropper 裁正方形 → 确认走独立头像端点。
 * 取消即关闭，不产生上传；成功后 me 缓存失效重取（hook 内），顶栏与页内头像随 me 刷新。
 */
const open = defineModel<boolean>({ required: true })

const emit = defineEmits<{ uploaded: [] }>()

const { t } = useI18n()

const upload = useUploadAvatar()

/** 选图后的 data URL（弹窗内 cropper 图源）；关闭弹窗即清，下次进入重新选图 */
const img = ref('')
// cropper 实例仅消费取结果一个面（包无类型，声明见 types/vue-cropper.d.ts）
const cropperRef = ref<{ getCropBlob(callback: (blob: Blob) => void): void } | null>(null)

function onFileChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) {
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    img.value = String(reader.result)
  }
  reader.readAsDataURL(raw)
}

function onCancel() {
  open.value = false
}

const uploading = ref(false)

function onConfirm() {
  if (!img.value || uploading.value) {
    return
  }
  uploading.value = true
  cropperRef.value?.getCropBlob(async (blob: Blob) => {
    const file = new File([blob], 'avatar.png', { type: blob.type || 'image/png' })
    try {
      await upload.mutateAsync(file)
      ElMessage.success(t('account.avatarUpdateSuccess'))
      open.value = false
      emit('uploaded')
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : t('account.avatarUpdateFailed'))
    } finally {
      uploading.value = false
    }
  })
}
</script>

<template>
  <el-dialog
    v-model="open"
    :title="t('account.avatarDialogTitle')"
    width="520px"
    destroy-on-close
    data-test="account-crop-dialog"
    @closed="img = ''"
  >
    <el-upload
      :auto-upload="false"
      :show-file-list="false"
      accept="image/*"
      :on-change="onFileChange"
      class="avatar-cropper__pick"
      data-test="account-avatar-pick"
    >
      <el-button type="primary" plain data-test="account-avatar-pick-button">
        {{ t('account.avatarPick') }}
      </el-button>
    </el-upload>
    <div v-if="img" class="avatar-cropper__box">
      <VueCropper
        ref="cropperRef"
        :img="img"
        output-type="png"
        :output-size="0.9"
        :auto-crop="true"
        :auto-crop-width="200"
        :auto-crop-height="200"
        :fixed="true"
        :fixed-number="[1, 1]"
        :center-box="true"
        :can-scale="true"
        :info="true"
        :full="false"
      />
    </div>
    <div v-else class="avatar-cropper__empty">{{ t('account.avatarPickHint') }}</div>
    <template #footer>
      <el-button data-test="account-crop-cancel" @click="onCancel">
        {{ t('account.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :disabled="!img"
        :loading="uploading"
        data-test="account-crop-confirm"
        @click="onConfirm"
      >
        {{ t('account.avatarConfirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.avatar-cropper__pick {
  margin-bottom: 12px;
}

.avatar-cropper__box {
  width: 100%;
  height: 300px;
}

.avatar-cropper__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 160px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
