import { describe, expect, it } from 'vitest'
import { matchesChampion, matchesHangul } from './hangulSearch'

describe('matchesHangul', () => {
  it('초성만으로 찾는다', () => {
    expect(matchesHangul('ㄱㄹ', '가렌')).toBe(true)
    expect(matchesHangul('ㄹㅅ', '리 신')).toBe(true)
    expect(matchesHangul('ㄴㄴ', '가렌')).toBe(false)
  })
  it('부분 문자열이어도 맞는다', () => {
    expect(matchesHangul('ㄹ', '가렌')).toBe(true)
    expect(matchesHangul('렌', '가렌')).toBe(true)
  })
  it('초성과 글자를 섞어 쓸 수 있다', () => {
    expect(matchesHangul('가ㄹ', '가렌')).toBe(true)
    expect(matchesHangul('나ㄹ', '가렌')).toBe(false)
  })
  it('평자음 질의는 쌍자음도 맞고 쌍자음 질의는 쌍자음만 맞는다', () => {
    expect(matchesHangul('ㅅㄹㅅ', '쓰레쉬')).toBe(true)
    expect(matchesHangul('ㅆ', '쓰레쉬')).toBe(true)
    expect(matchesHangul('ㅆ', '스카너')).toBe(false)
  })
  it('빈 질의는 모두 일치하고 질의가 더 길면 실패한다', () => {
    expect(matchesHangul('  ', '가렌')).toBe(true)
    expect(matchesHangul('ㄱㄹㅅ', '가렌')).toBe(false)
  })
})

describe('matchesChampion', () => {
  it('영문 키는 대소문자를 무시하고 부분 일치한다', () => {
    expect(matchesChampion('LEE', '리 신', 'LeeSin')).toBe(true)
    expect(matchesChampion('ㄹㅅ', '리 신', 'LeeSin')).toBe(true)
    expect(matchesChampion('zed', '제드', 'Zed')).toBe(true)
    expect(matchesChampion('ahri', '제드', 'Zed')).toBe(false)
  })
})
