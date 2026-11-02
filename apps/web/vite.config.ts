import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'
import { fileURLToPath, URL } from 'node:url'

import AutoImport from 'unplugin-auto-import/vite'
import { FileSystemIconLoader } from 'unplugin-icons/loaders'
import Icons from 'unplugin-icons/vite'
import Components from 'unplugin-vue-components/vite'
import vue from '@vitejs/plugin-vue'
import { configDefaults, defineConfig, type Plugin } from 'vitest/config'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 测试态不注入 EP 样式（vitest 外部化链路不处理 node_modules 的 css）
const epResolver = () => ElementPlusResolver({ importStyle: process.env.VITEST ? false : 'css' })

// 产物预生成 gzip（nginx gzip_static 生效前提）：文本类静态资产逐个写同名 .gz
const GZIP_EXTENSIONS = /\.(html|js|css|svg|json|txt)$/

function gzipAssets(): Plugin {
  function compress(dir: string) {
    for (const name of readdirSync(dir)) {
      const path = join(dir, name)
      if (statSync(path).isDirectory()) {
        compress(path)
      } else if (GZIP_EXTENSIONS.test(name)) {
        writeFileSync(`${path}.gz`, gzipSync(readFileSync(path), { level: 9 }))
      }
    }
  }
  return {
    name: 'gzip-assets',
    apply: 'build',
    closeBundle() {
      compress(fileURLToPath(new URL('./dist', import.meta.url)))
    },
  }
}

export default defineConfig({
  plugins: [
    vue(),
    gzipAssets(),
    // EP API 自动按需（仅限 Element Plus；Vue 核心 API 保持显式导入）
    AutoImport({
      resolvers: [epResolver()],
      dts: 'src/types/auto-imports.d.ts',
    }),
    // EP 组件模板自动按需注册
    Components({
      resolvers: [epResolver()],
      dts: 'src/types/components.d.ts',
    }),
    // 本地 SVG 图标集合：virtual:icons/app/<name>
    Icons({
      compiler: 'vue3',
      customCollections: {
        app: FileSystemIconLoader('./src/assets/icons'),
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // 后端接口无 /api 前缀：代理时去掉 /api 再转发
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  test: {
    environment: 'happy-dom',
    setupFiles: ['src/tests/setup.ts'],
    // e2e/ 归 Playwright，vitest 不收集
    exclude: [...configDefaults.exclude, 'e2e/**'],
    // admin-ui 组件层以源码态消费：包体与 EP 样式入口内联进 vitest 处理
    // （外部化模块的 .css 导入会走 node 原生加载报错；样式入口体量小，全量内联 EP 太重）
    server: {
      deps: { inline: [/@eladmin-plus\/admin-ui/, /element-plus[\\/].*style/] },
    },
    css: true,
  },
})
