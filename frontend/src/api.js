const publicPagePaths = new Set(['/', '/search', '/topics', '/solutions', '/articles', '/login'])
const publicDetailPattern = /^\/(topics|articles)\/\d+\/?$/

function isPublicPage(pathname) {
  return publicPagePaths.has(pathname) || publicDetailPattern.test(pathname)
}

export async function api(url, options = {}) {
  const token = localStorage.getItem('fanghu-token')
  const headers = { ...(options.headers || {}), ...(token ? { 'fanghu-token': token } : {}) }
  const response = await fetch(url, { ...options, headers })
  if (response.status === 401) {
    clearSession()
    if (!isPublicPage(window.location.pathname)) window.location.replace('/login')
  }
  return response
}
export function readRoles(){try{return JSON.parse(localStorage.getItem('fanghu-roles')||'[]')}catch{return []}}
export function readUsername(){return localStorage.getItem('fanghu-user')||'用户'}
export function hasRole(role){return readRoles().includes(role)}
export function hasPermission(permission){try{return JSON.parse(localStorage.getItem('fanghu-permissions')||'[]').includes(permission)}catch{return false}}
export function saveSession(data){localStorage.setItem('fanghu-token',data.token);localStorage.setItem('fanghu-user',data.user||'用户');localStorage.setItem('fanghu-roles',JSON.stringify(data.roles||[]));localStorage.setItem('fanghu-permissions',JSON.stringify(data.permissions||[]));window.dispatchEvent(new Event('fanghu-auth-change'))}
export function clearSession(){localStorage.removeItem('fanghu-token');localStorage.removeItem('fanghu-user');localStorage.removeItem('fanghu-roles');localStorage.removeItem('fanghu-permissions');window.dispatchEvent(new Event('fanghu-auth-change'))}
export async function refreshSession(){if(!localStorage.getItem('fanghu-token')||localStorage.getItem('fanghu-user'))return;const response=await api('/api/auth/session');if(response.ok)saveSession(await response.json())}
export function onAuthChange(handler){window.addEventListener('fanghu-auth-change',handler);window.addEventListener('storage',handler);return()=>{window.removeEventListener('fanghu-auth-change',handler);window.removeEventListener('storage',handler)}}
const homepageContactChangeEvent = 'fanghu-homepage-contact-change'
export function notifyHomepageContactChange(contact){window.dispatchEvent(new CustomEvent(homepageContactChangeEvent,{detail:contact||null}))}
export function onHomepageContactChange(handler){const listener=event=>handler(event.detail);window.addEventListener(homepageContactChangeEvent,listener);return()=>window.removeEventListener(homepageContactChangeEvent,listener)}
export function landingPath(roles=readRoles()){if(roles.includes('admin'))return '/admin';if(roles.includes('supplier'))return '/workbench';if(roles.includes('customer'))return '/customer/workbench';return '/'}
