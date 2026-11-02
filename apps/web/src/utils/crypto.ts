/** PEM（PUBLIC KEY）→ DER ArrayBuffer */
function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----[A-Z ]+-----/g, '').replace(/\s/g, '')
  const binary = atob(body)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

function toBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary)
}

/** 登录密码加密：Web Crypto 原生 RSA-OAEP（SHA-256，与后端解密参数对齐），不引 jsencrypt */
export async function encryptPassword(publicKeyPem: string, plain: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    'spki',
    pemToDer(publicKeyPem),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )
  const cipher = await crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    key,
    new TextEncoder().encode(plain),
  )
  return toBase64(cipher)
}
