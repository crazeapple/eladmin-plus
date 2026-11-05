<script setup lang="ts">
import { ElMessage, type FormInstance } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { useEmailSend } from '@/api/email'
import { useAuth } from '@/composables/use-auth'

defineOptions({ name: 'ToolsEmail' })

const { t } = useI18n()
const { hasAuth } = useAuth()

/** 单页发送表单：正文 textarea 直写 HTML 源码（html: true 透传后端），错误文案页内展示 */
const formRef = ref<FormInstance>()
const form = reactive({ to: '', subject: '', content: '' })

const rules = computed(() => ({
  to: [{ required: true, message: t('email.toRequired'), trigger: 'blur' }],
}))

const send = useEmailSend()
const sending = ref(false)

async function onSend() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  sending.value = true
  try {
    await send.mutateAsync({
      to: form.to,
      subject: form.subject,
      content: form.content,
      html: true,
    })
    ElMessage.success(t('email.sendSuccess'))
    form.subject = ''
    form.content = ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('email.sendFailed'))
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="email" data-test="email-page">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="72px"
      class="email__form"
      data-test="email-form"
    >
      <el-form-item :label="t('email.to')" prop="to">
        <el-input v-model="form.to" data-test="email-to" />
      </el-form-item>
      <el-form-item :label="t('email.subject')" prop="subject">
        <el-input v-model="form.subject" data-test="email-subject" />
      </el-form-item>
      <el-form-item :label="t('email.content')" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="12"
          :placeholder="t('email.contentPlaceholder')"
          data-test="email-content"
        />
      </el-form-item>
      <el-form-item v-if="hasAuth('email:send')">
        <el-button type="primary" :loading="sending" data-test="email-send" @click="onSend">
          {{ t('email.send') }}
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.email {
  padding: 16px;
}

.email__form {
  width: 640px;
  max-width: 100%;
  background-color: var(--el-bg-color, #fff);
  border-radius: 4px;
  padding: 20px 24px 8px;
}
</style>
