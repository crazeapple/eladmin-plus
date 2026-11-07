<script setup lang="ts">
import { PersonOutline } from '@vicons/ionicons5'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { useChangePassword, useClearAvatar, useMe, useUpdateProfile } from '@/api/profile'
import { broadcastLogout } from '@/api/auth-sync'
import { GENDER_OPTIONS } from '@/constants/gender'
import { useAvatarUrl } from '@/composables/use-avatar-url'
import { useMenusStore } from '@/stores/menus'
import { useSessionStore } from '@/stores/session'
import { useTagsViewStore } from '@/stores/tags-view'

import AvatarCropper from './avatar-cropper.vue'
import { buildAccountRules, buildPasswordRules } from './rules'

defineOptions({ name: 'Account' })

const { t } = useI18n()
const router = useRouter()

const activeTab = ref('profile')

const { data: me } = useMe()
const user = computed(() => me.value?.user ?? null)

/** 头像展示：me.avatarId → 存储域对象 URL；无头像回退默认占位 */
const avatarUrl = useAvatarUrl(computed(() => user.value?.avatarId))
const cropOpen = ref(false)

const clearAvatar = useClearAvatar()

/** 头像清空：二次确认（取消不调接口），成功后恢复默认占位 */
async function onClearAvatar() {
  try {
    await ElMessageBox.confirm(t('account.avatarClearConfirm'), t('account.avatarClearTitle'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await clearAvatar.mutateAsync()
    ElMessage.success(t('account.avatarClearSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('account.avatarClearFailed'))
  }
}

/** —— 资料表单：四字段全量替换语义，以 me 当前值为基线编辑 —— */

const formRef = ref<FormInstance>()
const form = reactive({ nickName: '', email: '', phone: '', gender: 0 })

watch(
  user,
  (value) => {
    if (!value) {
      return
    }
    form.nickName = value.nickName ?? ''
    form.email = value.email ?? ''
    form.phone = value.phone ?? ''
    form.gender = value.gender ?? 0
  },
  { immediate: true },
)

const rules = computed(() => buildAccountRules(t))

const updateProfile = useUpdateProfile()
const saving = ref(false)

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    await updateProfile.mutateAsync({
      nickName: form.nickName,
      email: form.email,
      phone: form.phone,
      gender: form.gender,
    })
    ElMessage.success(t('account.profileSaveSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('account.profileSaveFailed'))
  } finally {
    saving.value = false
  }
}

/** —— 改密：成功即该用户全部会话失效（含当前），按登出语义清态跳登录 —— */

const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules = computed(() => buildPasswordRules(t, () => pwdForm.newPassword))

const changePassword = useChangePassword()
const changing = ref(false)

async function onChangePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  changing.value = true
  try {
    await changePassword.mutateAsync({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    // 后端已踢全部会话：本地全清并广播其他 tab，跳登录页（不再调登出端点，会话已无效）
    useSessionStore().reset()
    useMenusStore().reset()
    useTagsViewStore().closeAll()
    broadcastLogout()
    await router.push('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('account.passwordChangeFailed'))
  } finally {
    changing.value = false
  }
}
</script>

<template>
  <div class="account" data-test="account-page">
    <el-tabs v-model="activeTab" class="account__tabs">
      <el-tab-pane :label="t('account.profileTab')" name="profile" data-test="account-tab-profile">
        <div class="profile">
          <div class="profile__avatar">
            <el-avatar :size="96" :src="avatarUrl || undefined" data-test="account-avatar">
              <span
                v-if="!avatarUrl"
                class="profile__avatar-fallback"
                data-test="account-avatar-default"
              >
                <person-outline />
              </span>
            </el-avatar>
            <div class="profile__avatar-actions">
              <el-button
                type="primary"
                plain
                data-test="account-avatar-change"
                @click="cropOpen = true"
              >
                {{ t('account.avatarChange') }}
              </el-button>
              <el-button
                v-if="user?.avatarId"
                type="danger"
                plain
                data-test="account-avatar-clear"
                @click="onClearAvatar"
              >
                {{ t('account.avatarClear') }}
              </el-button>
            </div>
          </div>
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="72px"
            class="profile__form"
            data-test="account-profile-form"
          >
            <el-form-item :label="t('account.nickName')" prop="nickName">
              <el-input v-model="form.nickName" data-test="account-profile-nickname" />
            </el-form-item>
            <el-form-item :label="t('account.email')" prop="email">
              <el-input v-model="form.email" data-test="account-profile-email" />
            </el-form-item>
            <el-form-item :label="t('account.phone')" prop="phone">
              <el-input v-model="form.phone" data-test="account-profile-phone" />
            </el-form-item>
            <el-form-item :label="t('account.gender')" prop="gender">
              <el-radio-group v-model="form.gender" data-test="account-profile-gender">
                <el-radio
                  v-for="option in GENDER_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ t(option.labelKey) }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="saving"
                data-test="account-profile-save"
                @click="onSave"
              >
                {{ t('account.save') }}
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
      <el-tab-pane
        :label="t('account.passwordTab')"
        name="password"
        data-test="account-tab-password"
        lazy
      >
        <el-form
          ref="pwdFormRef"
          :model="pwdForm"
          :rules="passwordRules"
          label-width="96px"
          class="password"
          data-test="account-password-form"
        >
          <el-form-item :label="t('account.oldPassword')" prop="oldPassword">
            <el-input
              v-model="pwdForm.oldPassword"
              type="password"
              show-password
              data-test="account-password-old"
            />
          </el-form-item>
          <el-form-item :label="t('account.newPassword')" prop="newPassword">
            <el-input
              v-model="pwdForm.newPassword"
              type="password"
              show-password
              data-test="account-password-new"
            />
          </el-form-item>
          <el-form-item :label="t('account.confirmPassword')" prop="confirmPassword">
            <el-input
              v-model="pwdForm.confirmPassword"
              type="password"
              show-password
              data-test="account-password-confirm"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="changing"
              data-test="account-password-save"
              @click="onChangePassword"
            >
              {{ t('account.passwordChange') }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
    <avatar-cropper v-model="cropOpen" />
  </div>
</template>

<style scoped lang="scss">
.account {
  padding: 16px;
}

.account__tabs {
  background-color: var(--el-bg-color, #fff);
  border-radius: 4px;
  padding: 8px 16px 16px;
}

.profile {
  display: flex;
  gap: 48px;
  align-items: flex-start;
  padding: 12px 0;
}

.profile__avatar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
  flex: none;
}

.profile__avatar-fallback {
  display: inline-flex;
  font-size: 40px;
  color: var(--el-text-color-secondary);

  /* 缺省头像为具名 ionicons 组件（裸 svg 无宽高属性），按 em 盒给尺寸 */
  svg {
    width: 1em;
    height: 1em;
  }
}

.profile__avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .el-button {
    width: 112px;
    margin: 0;
  }
}

.profile__form {
  width: 420px;
  max-width: 100%;
}
</style>
