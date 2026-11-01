import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig(({mode})=>{
  // 与浏览器端读取同一个前端配置，开发代理不再重复维护后端地址
  const env=loadEnv(mode,process.cwd(),'VITE_');
  const target=env.VITE_BACKEND_URL?.trim().replace(/\/+$/,'');
  // 构建产物输出到项目根 dist/，构建后放入现有 Nginx webroot
  return {plugins:[vue()],build:{outDir:'../dist',emptyOutDir:true},server:{proxy:target?{'/api':target,'/m':target,'/actuator':target}:undefined}};
});
