// 한 팀의 확인 표: 팀 표시 합계 4개 + 탑→서포터 순 5행(각 행에 결과창 행 번호) + 밴 5칸.
// 포지션 드롭다운을 바꾸면 같은 팀의 해당 포지션 행과 맞바꾼다(reviewModel.swapPosition).
// 설계: docs/design/m4-design.md §6 TeamTable
import type { ChampionInfo, DraftTeam, RowInput, TeamInput } from '../../../api/registrationTypes'
import { POSITION_LABEL, POSITIONS, type Player } from '../../../api/types'
import { BanGrid } from './BanGrid'
import { ChampionPicker } from './ChampionPicker'
import { ManualPlayerPicker } from '../set-form/ManualPlayerPicker'
import { PlayerPicker } from './PlayerPicker'
import {
  replaceRow,
  rowKey,
  rowsByLane,
  swapPosition,
  updateRow,
  updateTeam,
  type TeamsState,
  type TeamKey,
} from './reviewModel'

type TeamTableProps = {
  teamKey: TeamKey
  teamLabel: string // 예: "위쪽 팀"
  heading: string // 예: "위쪽 팀 · A팀 · 블루"
  team: TeamInput
  draftTeam: DraftTeam | null // null이면 초안 없는 화면(세트 수정·직접 입력): 선수는 ManualPlayerPicker로 고른다
  banLabels: string[]
  imageUrl: string | null
  champions: ChampionInfo[]
  championsById: ReadonlyMap<string, ChampionInfo>
  restricted: ReadonlySet<string>
  players: Player[] | undefined
  issues: Record<string, string[]>
  aliasConflicts: ReadonlyMap<string, string> // 정규화한 닉네임 → 설명 (저장 때 ALIAS_CONFLICT)
  onUpdate: (change: (state: TeamsState) => TeamsState) => void
}

function toCount(value: string): number {
  const parsed = Math.floor(Number(value))
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0
}

// label은 접근성 이름(전체), shortLabel은 화면에 보이는 짧은 이름
function NumberInput({
  label,
  shortLabel,
  value,
  onChange,
}: {
  label: string
  shortLabel: string
  value: number
  onChange: (value: number) => void
}) {
  return (
    <label className="number-field">
      <span aria-hidden="true">{shortLabel}</span>
      <input
        type="number"
        min={0}
        aria-label={label}
        value={value}
        onChange={(event) => onChange(toCount(event.target.value))}
      />
    </label>
  )
}

const TOTAL_FIELDS = [
  ['displayedKills', '킬'],
  ['displayedDeaths', '데스'],
  ['displayedAssists', '어시'],
  ['displayedGold', '골드'],
] as const

const STAT_FIELDS = [
  ['kills', 'K'],
  ['deaths', 'D'],
  ['assists', 'A'],
  ['cs', 'CS'],
  ['gold', '골드'],
] as const

export function TeamTable({
  teamKey,
  teamLabel,
  heading,
  team,
  draftTeam,
  banLabels,
  imageUrl,
  champions,
  championsById,
  restricted,
  players,
  issues,
  aliasConflicts,
  onUpdate,
}: TeamTableProps) {
  return (
    <section className="team-table panel" aria-label={teamLabel}>
      <h2>{heading}</h2>
      <div className="team-totals">
        <span className="muted-text">결과창 팀 합계</span>
        {TOTAL_FIELDS.map(([field, name]) => (
          <NumberInput
            key={field}
            label={`${teamLabel} 합계 ${name}`}
            shortLabel={name}
            value={team[field]}
            onChange={(value) => onUpdate((state) => updateTeam(state, teamKey, { [field]: value }))}
          />
        ))}
      </div>

      {rowsByLane(team).map((row: RowInput) => {
        const draftRow = draftTeam?.rows.find((d) => d.screenRow === row.screenRow)
        const rowLabel = `${teamLabel} 결과창 ${row.screenRow}행`
        const slotIssues = issues[rowKey(teamKey, row.screenRow)] ?? []
        const conflict = aliasConflicts.get(row.nickname.trim().toLowerCase()) ?? null
        return (
          <div key={row.screenRow} role="group" aria-label={rowLabel} className={`review-row${slotIssues.length ? ' has-issue' : ''}`}>
            <div className="row-lane">
              <span className="row-no">결과창 {row.screenRow}행</span>
              <select
                aria-label={`${rowLabel} 포지션`}
                value={row.position}
                onChange={(event) =>
                  onUpdate((state) => swapPosition(state, teamKey, row.screenRow, event.target.value as RowInput['position']))
                }
              >
                {POSITIONS.map((position) => (
                  <option key={position} value={position}>
                    {POSITION_LABEL[position]}
                  </option>
                ))}
              </select>
            </div>

            {draftTeam === null ? (
              <ManualPlayerPicker
                rowLabel={rowLabel}
                row={row}
                players={players}
                conflict={conflict}
                onChange={(next) => onUpdate((state) => replaceRow(state, teamKey, next))}
              />
            ) : draftRow ? (
              <PlayerPicker
                rowLabel={rowLabel}
                row={row}
                draftRow={draftRow}
                players={players}
                conflict={conflict}
                onChange={(next) => onUpdate((state) => replaceRow(state, teamKey, next))}
              />
            ) : (
              <span className="muted-text">초안에 이 행이 없습니다.</span>
            )}

            <ChampionPicker
              label={`${rowLabel} 챔피언`}
              value={row.championKey}
              guess={draftRow?.champion ?? null}
              box={draftRow?.portraitBox ?? null}
              imageUrl={imageUrl}
              champions={champions}
              championsById={championsById}
              restricted={restricted}
              issues={slotIssues}
              onChange={(id) => onUpdate((state) => updateRow(state, teamKey, row.screenRow, { championKey: id }))}
            />

            <div className="row-stats">
              {STAT_FIELDS.map(([field, name]) => (
                <NumberInput
                  key={field}
                  label={`${rowLabel} ${name}`}
                  shortLabel={name}
                  value={row[field]}
                  onChange={(value) => onUpdate((state) => updateRow(state, teamKey, row.screenRow, { [field]: value }))}
                />
              ))}
            </div>
          </div>
        )
      })}

      <h3>밴</h3>
      <BanGrid
        teamKey={teamKey}
        teamLabel={teamLabel}
        bans={team.bans}
        draftBans={draftTeam?.bans ?? []}
        labels={banLabels}
        imageUrl={imageUrl}
        champions={champions}
        championsById={championsById}
        issues={issues}
        onUpdate={onUpdate}
      />
    </section>
  )
}
