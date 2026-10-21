// 담당자 로그인일 때만 보이는 "원본 보기". 누르면 이미지를 blob으로 받아 확대해 보여 준다. 방문자에게는 아무것도 그리지 않는다.
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { fetchAdminSetImage } from '../../api/adminImage'
import { useAdminMe } from '../../auth/useAdminMe'
import { ErrorNotice } from '../../components/ErrorNotice'
import { useObjectUrl } from '../admin/register/useObjectUrl'
import '../../styles/records.css'

export function OriginalImageButton({ setId }: { setId: number }) {
  const me = useAdminMe()
  const [blob, setBlob] = useState<Blob | null>(null)
  const url = useObjectUrl(blob)
  const load = useMutation({ mutationFn: () => fetchAdminSetImage(setId), onSuccess: setBlob })

  if (!me.data) return null

  return (
    <div className="original-image">
      <button type="button" onClick={() => load.mutate()} disabled={load.isPending}>
        {load.isPending ? '불러오는 중…' : '원본 보기'}
      </button>
      {load.isError && <ErrorNotice error={load.error} />}
      {blob && (
        <div className="original-image-view" role="dialog" aria-label="원본 결과창">
          <button type="button" onClick={() => setBlob(null)}>
            닫기
          </button>
          {url && <img src={url} alt="원본 결과창 이미지" />}
        </div>
      )}
    </div>
  )
}
