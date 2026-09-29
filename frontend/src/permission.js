import { hasPermission } from './api'

function applyPermission(element, binding) {
  const required = Array.isArray(binding.value) ? binding.value : [binding.value]
  const allowed = required.filter(Boolean).some(hasPermission)
  element.hidden = !allowed
  element.setAttribute('aria-hidden', allowed ? 'false' : 'true')
}

export const permissionDirective = {
  mounted: applyPermission,
  updated: applyPermission
}
