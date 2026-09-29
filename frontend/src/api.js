const publicPagePaths = new Set(['/', '/search', '/topics', '/solutions', '/articles', '/login'])
const publicDetailPattern = /^\/(topics|articles)\/\d+\/?$/

function isPublicPage(pathname) {
  return publicPagePaths.has(pathname) || publicDetailPattern.test(pathname)
}

export async function api(url, options = {}) {
  const token = sessionStorage.getItem('fanghu-token')
  const headers = {
    ...(options.headers || {}),
    ...(token ? { Authorization: `Bearer ${token}`, 'fanghu-token': token, clientid: 'e5cd7e4891bf95d1d19206ce24a7b32e' } : {})
  }
  const response = await fetch(url, { ...options, headers })
  if (response.status === 401) {
    clearSession()
    if (!isPublicPage(window.location.pathname)) window.location.replace('/login')
  }
  return response
}
export async function readJson(response, fallback = null) {
  const text = await response.text()
  if (!text.trim()) return fallback
  try { return JSON.parse(text) } catch { return fallback }
}
export async function readResponseMessage(response, fallback = '服务暂不可用，请稍后重试') {
  const body = await readJson(response, {})
  return body?.message || body?.msg || fallback
}
export function readRoles(){try{return JSON.parse(sessionStorage.getItem('fanghu-roles')||'[]')}catch{return []}}
export function readUsername(){return sessionStorage.getItem('fanghu-user')||'用户'}
export function hasRole(role){return readRoles().includes(role)}
export function readPermissions(){try{return JSON.parse(sessionStorage.getItem('fanghu-permissions')||'[]')}catch{return []}}
export function hasPermission(permission){return readPermissions().includes(permission)}
export function saveSession(data){sessionStorage.setItem('fanghu-token',data.token);sessionStorage.setItem('fanghu-user',data.user||'用户');sessionStorage.setItem('fanghu-roles',JSON.stringify(data.roles||[]));sessionStorage.setItem('fanghu-permissions',JSON.stringify(data.permissions||[]));window.dispatchEvent(new Event('fanghu-auth-change'))}
export function clearSession(){sessionStorage.removeItem('fanghu-token');sessionStorage.removeItem('fanghu-user');sessionStorage.removeItem('fanghu-roles');sessionStorage.removeItem('fanghu-permissions');window.dispatchEvent(new Event('fanghu-auth-change'))}
export function normalizeRoles(roles=[]){return [...new Set(roles.map(role=>role==='superadmin'?'admin':role))]}
export async function loadNativeSession(token=sessionStorage.getItem('fanghu-token')){
  if(!token)return null
  sessionStorage.setItem('fanghu-token',token)
  const response=await api('/api/auth/session')
  if(!response.ok)throw new Error('无法读取登录用户信息')
  const body=await readJson(response, {})
  if(!response.ok || !body.user)throw new Error(body.msg||body.message||'无法读取登录用户信息')
  const session={token,user:body.user||'用户',roles:normalizeRoles(body.roles||[]),permissions:body.permissions||[]}
  saveSession(session)
  return session
}
export async function refreshSession(){if(!sessionStorage.getItem('fanghu-token'))return;try{await loadNativeSession()}catch{clearSession()}}
export function onAuthChange(handler){window.addEventListener('fanghu-auth-change',handler);window.addEventListener('storage',handler);return()=>{window.removeEventListener('fanghu-auth-change',handler);window.removeEventListener('storage',handler)}}
const homepageContactChangeEvent = 'fanghu-homepage-contact-change'
export function notifyHomepageContactChange(contact){window.dispatchEvent(new CustomEvent(homepageContactChangeEvent,{detail:contact||null}))}
export function onHomepageContactChange(handler){const listener=event=>handler(event.detail);window.addEventListener(homepageContactChangeEvent,listener);return()=>window.removeEventListener(homepageContactChangeEvent,listener)}
export function landingPath(roles=readRoles()){if(roles.includes('admin'))return '/admin';if(roles.includes('supplier'))return '/workbench';if(roles.includes('customer'))return '/customer/workbench';return '/'}
