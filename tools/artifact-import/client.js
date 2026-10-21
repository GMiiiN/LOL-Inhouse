// 관리자 API용 최소 HTTP 클라이언트(import.js, import-matches.js 공용). 외부 패키지 없음.
'use strict'

// 쿠키를 유지하는 최소 클라이언트. 변경 요청에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN으로 보낸다.
class Client {
  constructor(server) {
    this.server = server.replace(/\/$/, '')
    this.cookies = new Map()
  }

  storeCookies(res) {
    for (const line of res.headers.getSetCookie()) {
      const [pair] = line.split(';')
      const eq = pair.indexOf('=')
      const name = pair.slice(0, eq).trim()
      const value = pair.slice(eq + 1)
      if (value === '') this.cookies.delete(name)
      else this.cookies.set(name, value)
    }
  }

  async request(method, path, { json, form } = {}, retried = false) {
    const headers = { Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ') }
    let body
    if (method !== 'GET') {
      const xsrf = this.cookies.get('XSRF-TOKEN')
      if (xsrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(xsrf)
    }
    if (json !== undefined) {
      headers['Content-Type'] = 'application/json'
      body = JSON.stringify(json)
    }
    if (form) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded'
      body = new URLSearchParams(form).toString()
    }
    const res = await fetch(this.server + path, { method, headers, body, redirect: 'manual' })
    this.storeCookies(res)
    const text = await res.text()
    let data = null
    try {
      data = text ? JSON.parse(text) : null
    } catch {
      // 본문이 JSON이 아니면 data는 null
    }
    if (res.status === 403 && !retried && data && data.code === 'CSRF_INVALID') {
      await this.csrf()
      return this.request(method, path, { json, form }, true)
    }
    return { status: res.status, data }
  }

  csrf() {
    return this.request('GET', '/api/csrf')
  }
}

module.exports = { Client }
