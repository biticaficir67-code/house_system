export function errorText(error) {
  return error?.response?.data?.message || error?.message || '请求失败，请稍后重试'
}

export function nullableNumber(value) {
  return value === '' || value === null || value === undefined ? null : Number(value)
}

export function moneyWan(value) {
  return value === null || value === undefined || value === '' ? '—' : (Number(value) / 10000).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

export function wanToYuan(value) {
  if (value === null || value === undefined || value === '') return null
  const text = String(value).trim()
  if (!/^\d+(\.\d{1,6})?$/.test(text)) throw new Error('预算请填写非负数字，最多6位小数')
  const [whole, decimal = ''] = text.split('.')
  const integer = (whole + decimal.padEnd(4, '0').slice(0, 4)).replace(/^0+(?=\d)/, '')
  const cents = decimal.slice(4).padEnd(2, '0')
  return `${integer}.${cents}`
}

export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function csvCell(value) {
  let text = String(value ?? '')
  if (/^[\s]*[=+@-]/.test(text)) text = "'" + text
  return '"' + text.replace(/"/g, '""') + '"'
}
