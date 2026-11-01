// 构建配置留空时复用页面入口，使同一份前端产物适用于不同 Nginx 域名和 IP
const backendBaseUrl=(import.meta.env.VITE_BACKEND_URL?.trim()||window.location.origin).replace(/\/+$/,'');

/**
 * 使用管理请求共用的后端地址生成完整链接，避免从后端重复获取地址配置
 *
 * @param {string} path 以斜杠开头的管理、健康检查或 Mock 路径
 * @return {string} 浏览器和 HTTP 调用方可访问的完整地址
 */
export function backendUrl(path){
  return `${backendBaseUrl}${path}`;
}

/**
 * 开发阶段通过读取同一配置的 Vite 代理请求，生产阶段直接使用统一访问入口
 *
 * @param {string} path 以斜杠开头的请求路径
 * @param {RequestInit} [options] 请求方法、响应头及正文
 * @return {Promise<Response>} 后端 HTTP 响应
 */
export function fetchBackend(path,options){
  // 开发页面和后端端口不同，通过代理保持浏览器请求同源，不新增跨域配置
  return fetch(import.meta.env.DEV?path:backendUrl(path),options);
}
