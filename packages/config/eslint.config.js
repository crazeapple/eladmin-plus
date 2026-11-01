import js from '@eslint/js'
import configPrettier from 'eslint-config-prettier'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  {
    ignores: ['**/node_modules/', '**/dist/', '**/target/', '**/build/', '.mira/', '.worktrees/'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
        sourceType: 'module',
        extraFileExtensions: ['.vue'],
      },
    },
  },
  configPrettier,
)
