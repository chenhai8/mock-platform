import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({plugins:[vue()],build:{outDir:'../backend/src/main/resources/static',emptyOutDir:false},server:{proxy:{'/api':'http://127.0.0.1:8080','/m':'http://127.0.0.1:8080','/actuator':'http://127.0.0.1:8080'}}});
