// Blob(업로드한 결과창)을 <img>·CSS에서 쓸 수 있는 objectURL로 바꾸고, 바뀌거나 사라질 때 해제한다.
import { useEffect, useState } from 'react'

export function useObjectUrl(blob: Blob | null): string | null {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    // jsdom에는 URL.createObjectURL이 없다. 없으면 미리보기만 생략한다.
    if (!blob || typeof URL.createObjectURL !== 'function') {
      // StrictMode가 효과를 두 번 돌려도 URL을 다시 만들어야 하므로 렌더 중 계산 대신 효과 안에서 만든다
      // oxlint-disable-next-line react/set-state-in-effect
      setUrl(null)
      return undefined
    }
    const created = URL.createObjectURL(blob)
    // oxlint-disable-next-line react/set-state-in-effect
    setUrl(created)
    return () => URL.revokeObjectURL(created)
  }, [blob])
  return url
}
