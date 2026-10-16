// 아직 구현되지 않은 화면의 자리표시자. 어느 마일스톤이 채우는지만 보여 준다. 노트: docs/study/03-frontend-skeleton.md
type PlaceholderPageProps = {
  title: string
  milestone: string
}

export function PlaceholderPage({ title, milestone }: PlaceholderPageProps) {
  return (
    <section className="placeholder-page">
      <h1>{title}</h1>
      <p>이 화면은 {milestone}에서 구현합니다.</p>
    </section>
  )
}
