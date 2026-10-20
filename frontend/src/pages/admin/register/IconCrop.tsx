// 업로드한 원본에서 박스(0~1000 정규화, [ymin, xmin, ymax, xmax])만 잘라 보여 주는 작은 칸.
// canvas 없이 CSS background로 자른다: 배경을 박스 크기 역수만큼 키우고 박스 왼쪽 위가 칸 왼쪽 위에 오도록 옮긴다.
import type { Box4 } from '../../../api/registrationTypes'

type IconCropProps = {
  imageUrl: string | null
  box: Box4
  size?: number
  label: string
}

export function IconCrop({ imageUrl, box, size = 44, label }: IconCropProps) {
  const [ymin, xmin, ymax, xmax] = box
  const width = Math.max((xmax - xmin) / 1000, 0.001)
  const height = Math.max((ymax - ymin) / 1000, 0.001)
  // background-position %는 (칸 - 배경) 크기에 대한 비율이라 x0 / (1 - w)로 계산한다. 박스가 전체면 0.
  const positionX = width >= 1 ? 0 : (xmin / 1000 / (1 - width)) * 100
  const positionY = height >= 1 ? 0 : (ymin / 1000 / (1 - height)) * 100
  return (
    <span
      className="icon-crop"
      role="img"
      aria-label={label}
      style={{
        width: size,
        height: size,
        backgroundImage: imageUrl ? `url(${imageUrl})` : undefined,
        backgroundSize: `${100 / width}% ${100 / height}%`,
        backgroundPosition: `${positionX}% ${positionY}%`,
      }}
    />
  )
}
