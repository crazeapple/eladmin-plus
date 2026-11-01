import js from '@eslint/js'
import configPrettier from 'eslint-config-prettier'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  {
    ignores: [
      '**/node_modules/',
      '**/dist/',
      '**/target/',
      '**/build/',
      '.mira/',
      '.worktrees/',
      'apps/web/src/types/auto-imports.d.ts',
      'apps/web/src/types/components.d.ts',
      'apps/web/src/api/generated/',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    languageOptions: {
      globals: { ...globals.browser },
      parserOptions: {
        parser: tseslint.parser,
        sourceType: 'module',
        extraFileExtensions: ['.vue'],
      },
    },
  },
  {
    rules: {
      // 路由页面组件 name 必须与路由 name 一致（keep-alive 缓存契约），单词名合法
      'vue/multi-word-component-names': 'off',
    },
  },
  configPrettier,
)
