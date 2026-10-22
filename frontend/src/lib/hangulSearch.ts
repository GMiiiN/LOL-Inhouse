// 한글 초성 검색. 챔피언 이름·영문 키를 찾을 때 쓴다.
// 규칙: 대소문자·공백 무시, 부분 문자열 일치. 질의의 자음(ㄱ~ㅎ)은 그 초성을 가진 글자와 맞고,
// 평자음(ㄱㄷㅂㅅㅈ)은 같은 쌍자음(ㄲㄸㅃㅆㅉ)도 허용한다. 쌍자음 질의(ㅆ)는 쌍자음만 맞는다.
const INITIALS = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ'
const SYLLABLE_START = 0xac00
const SYLLABLE_END = 0xd7a3
const DOUBLE_OF: Record<string, string> = { ㄱ: 'ㄲ', ㄷ: 'ㄸ', ㅂ: 'ㅃ', ㅅ: 'ㅆ', ㅈ: 'ㅉ' }

function initialOf(char: string): string | null {
  const code = char.charCodeAt(0)
  if (code < SYLLABLE_START || code > SYLLABLE_END) return null
  return INITIALS[Math.floor((code - SYLLABLE_START) / 588)]
}

function charMatches(queryChar: string, targetChar: string): boolean {
  if (queryChar === targetChar) return true
  if (!INITIALS.includes(queryChar)) return false
  const initial = initialOf(targetChar)
  return initial !== null && (initial === queryChar || initial === DOUBLE_OF[queryChar])
}

function normalize(text: string): string {
  return text.replace(/\s+/g, '').toLowerCase()
}

function containsMatch(query: string, target: string): boolean {
  for (let start = 0; start + query.length <= target.length; start++) {
    if ([...query].every((char, i) => charMatches(char, target[start + i]))) return true
  }
  return false
}

// 빈 질의는 모두 일치
export function matchesHangul(query: string, target: string): boolean {
  const q = normalize(query)
  return q === '' || containsMatch(q, normalize(target))
}

export function matchesChampion(query: string, name: string, key: string): boolean {
  return matchesHangul(query, name) || matchesHangul(query, key)
}
